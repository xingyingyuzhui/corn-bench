#!/usr/bin/env python3
"""Local PTY pair and optional Android raw RTU bridge. No protocol implementation."""
import argparse
import collections
import datetime
import errno
import json
import os
from pathlib import Path
import pty
import selectors
import signal
import socket
import subprocess
import sys
import time
import tty


class WriteQueue:
    """One destination FIFO. Partial writes and EAGAIN never advance its head."""
    def __init__(self, writer, progress=None):
        self.writer = writer
        self.progress = progress or (lambda _count: None)
        self.items = collections.deque()

    @property
    def tail_due(self):
        return self.items[-1][0] if self.items else 0.

    def append(self, data, due):
        # A later submission cannot overtake previously submitted bytes.
        if data:
            self.items.append([max(due, self.tail_due), bytes(data), 0])

    def append_frame(self, data, now, delay, chunk_size, gap):
        size = chunk_size or len(data)
        start = max(now + delay, self.tail_due + gap if self.items else 0.)
        for index in range(0, len(data), size):
            self.append(data[index:index+size], start + (index//size)*gap)

    def flush(self, now):
        budget = 65536
        while self.items and self.items[0][0] <= now and budget > 0:
            item = self.items[0]
            try:
                written = self.writer(item[1][item[2]:item[2]+budget])
            except (BlockingIOError, InterruptedError):
                return
            if written <= 0:
                raise BrokenPipeError('Destination closed during queued write')
            item[2] += written
            budget -= written
            self.progress(written)
            if item[2] == len(item[1]):
                self.items.popleft()


def owned_process(state):
    """Only claim the launch identity of this exact daemon, including PID reuse."""
    if not state.get('running'):
        return False
    command = subprocess.run(['ps', '-p', str(state['pid']), '-o', 'command='],
                             capture_output=True, text=True).stdout
    if str(Path(__file__).resolve()) not in command:
        return False
    if state.get('token'):
        return state['token'] in command
    started = subprocess.run(['ps', '-p', str(state['pid']), '-o', 'lstart='],
                             capture_output=True, text=True).stdout.strip()
    return bool(state.get('process_started')) and started == state['process_started']


def private_write(path, value):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def serve(args):
    os.umask(0o077)
    directory = Path(args.state_dir).expanduser().resolve()
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    state_path = directory / 'state.json'
    # A lock prevents two relays from claiming the same state directory.
    import fcntl
    lock = open(directory / 'relay.lock', 'a')
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        raise SystemExit('A relay already owns this state directory')
    selector = selectors.DefaultSelector()
    masters, slaves, paths = [], [], []
    for side in ('A', 'B'):
        master, slave = pty.openpty()
        tty.setraw(slave)
        os.set_blocking(master, False)
        masters.append(master)
        slaves.append(slave)
        paths.append(os.ttyname(slave))
        selector.register(master, selectors.EVENT_READ, side)
    log = open(directory / 'traffic.jsonl', 'a', buffering=1)
    os.chmod(directory / 'traffic.jsonl', 0o600)
    counts = collections.Counter()
    process_started = subprocess.run(['ps', '-p', str(os.getpid()), '-o', 'lstart='],
                                     capture_output=True, text=True).stdout.strip()
    state = dict(process_started=process_started, pid=os.getpid(), token=args.token, started_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                 port_a=paths[0], port_b=paths[1], bridge_host=args.bridge_host,
                 bridge_port=args.bridge_port, bridge_connected=False,
                 bridge_connection_scope='host TCP only; ADB backend readiness requires successful RTU exchange',
                 running=True,
                 faults=dict(chunk_size=args.chunk_size, delay_ms=args.delay_ms, echo=args.echo,
                             drop_every=args.drop_every, corrupt_every=args.corrupt_every,
                             frame_gap_ms=args.frame_gap_ms))
    running = True
    def stop(*_):
        nonlocal running
        running = False
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    def record(event, data=b'', **extra):
        log.write(json.dumps(dict(time=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                  monotonic=time.monotonic(), event=event, hex=data.hex(' '), **extra)) + '\n')
    def save():
        state['counts'] = dict(counts)
        private_write(state_path, state)
    pending = {'A': bytearray(), 'B': bytearray()}
    last = dict(A=0., B=0.)
    queues = {fd: WriteQueue(lambda data, fd=fd: os.write(fd, data)) for fd in masters}
    def frame(side, now):
        data = bytes(pending[side]); pending[side].clear()
        counts[side + '_frames'] += 1
        record(side + '_frame', data)
        source, target = (masters[0], masters[1]) if side == 'A' else (masters[1], masters[0])
        number = counts[side + '_frames']
        if args.echo:
            queues[source].append(data, now)
        if args.drop_every and number % args.drop_every == 0:
            counts['dropped'] += 1
            record(side + '_drop', data)
            return
        if args.corrupt_every and number % args.corrupt_every == 0:
            data = data[:-1] + bytes([data[-1] ^ 1])
            counts['corrupted'] += 1
            record(side + '_corrupt_last_byte', data)
        queues[target].append_frame(data, now, args.delay_ms/1000,
                                    args.chunk_size, args.chunk_gap_ms/1000)
    bridge = None
    bridge_serial = None
    next_connect = 0.
    next_save = 0.
    def disconnect():
        nonlocal bridge, bridge_serial, next_connect
        for item in (bridge, bridge_serial):
            if item is not None:
                fd = item.fileno() if hasattr(item, 'fileno') else item
                queue = queues.pop(fd, None)
                if queue and queue.items:
                    counts['bridge_disconnect_discarded_bytes'] += sum(len(data)-offset for _, data, offset in queue.items)
                try: selector.unregister(item)
                except (KeyError, ValueError): pass
                item.close() if hasattr(item, 'close') else os.close(item)
        bridge = bridge_serial = None
        state['bridge_connected'] = False
        next_connect = time.monotonic() + .5
    save()
    print(json.dumps(state), flush=True)
    try:
        while running:
            now = time.monotonic()
            if args.bridge_port and bridge is None and now >= next_connect:
                try:
                    bridge = socket.create_connection((args.bridge_host, args.bridge_port), timeout=.2)
                    bridge.setblocking(False)
                    bridge_serial = os.open(paths[1], os.O_RDWR | os.O_NONBLOCK | os.O_NOCTTY)
                    tty.setraw(bridge_serial)
                    def progress(name, count):
                        counts[name] += count
                    queues[bridge.fileno()] = WriteQueue(bridge.send, lambda count: progress('serial_to_tcp_bytes', count))
                    queues[bridge_serial] = WriteQueue(lambda data: os.write(bridge_serial, data), lambda count: progress('tcp_to_serial_bytes', count))
                    selector.register(bridge, selectors.EVENT_READ, 'TCP')
                    selector.register(bridge_serial, selectors.EVENT_READ, 'SERIAL')
                    state['bridge_connected'] = True
                    counts['bridge_connections'] += 1
                    record('bridge_connected')
                except OSError:
                    disconnect()
            for key, _ in selector.select(.002):
                side = key.data
                try:
                    data = key.fileobj.recv(65536) if side == 'TCP' else os.read(key.fd, 65536)
                    if not data:
                        if side in ('TCP', 'SERIAL'): disconnect()
                        continue
                    if side in pending:
                        pending[side].extend(data)
                        last[side] = time.monotonic()
                        counts[side + '_bytes'] += len(data)
                    elif side == 'TCP':
                        queues[bridge_serial].append(data, time.monotonic())
                        record('TCP_to_B', data)
                    else:
                        queues[bridge.fileno()].append(data, time.monotonic())
                        record('B_to_TCP', data)
                except BlockingIOError:
                    continue
                except OSError as error:
                    if side in ('TCP', 'SERIAL'): disconnect()
                    elif error.errno not in (errno.EAGAIN, errno.EIO): raise
            now = time.monotonic()
            for side in pending:
                if pending[side] and now-last[side] >= args.frame_gap_ms/1000:
                    frame(side, now)
            for fd, queue in list(queues.items()):
                if queues.get(fd) is not queue:
                    continue
                try:
                    queue.flush(now)
                except OSError:
                    if fd in masters:
                        raise
                    disconnect()
            if now >= next_save:
                save(); next_save = now+.25
    finally:
        disconnect()
        state['running'] = False
        save()
        for fd in masters+slaves: os.close(fd)
        selector.close()
        log.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('serve', 'start', 'status', 'stop'))
    parser.add_argument('--state-dir', default='/tmp/modbus-rtu-lab')
    parser.add_argument('--bridge-host', default='127.0.0.1')
    parser.add_argument('--bridge-port', type=int, default=0)
    parser.add_argument('--chunk-size', type=int, default=0)
    parser.add_argument('--chunk-gap-ms', type=float, default=1)
    parser.add_argument('--delay-ms', type=float, default=0)
    parser.add_argument('--frame-gap-ms', type=float, default=4)
    parser.add_argument('--echo', action='store_true')
    parser.add_argument('--drop-every', type=int, default=0)
    parser.add_argument('--corrupt-every', type=int, default=0)
    parser.add_argument('--token', default='')
    args = parser.parse_args()
    for name in ('chunk_size','chunk_gap_ms','delay_ms','frame_gap_ms','drop_every','corrupt_every'):
        if getattr(args,name) < 0: parser.error(name+' must be nonnegative')
    directory = Path(args.state_dir).expanduser().resolve()
    if args.command == 'serve': return serve(args)
    if args.command == 'start':
        import uuid
        directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        token = uuid.uuid4().hex
        with open(directory/'daemon.log','ab') as output:
            os.chmod(directory/'daemon.log',0o600)
            process = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), 'serve', *sys.argv[2:], '--token', token], stdout=output, stderr=output, start_new_session=True)
        for _ in range(100):
            time.sleep(.05)
            if process.poll() is not None: raise SystemExit('Relay failed; inspect '+str(directory/'daemon.log'))
            try:
                state = json.loads((directory/'state.json').read_text())
                if state.get('token') == token: print(json.dumps(state,indent=2)); return
            except (OSError,ValueError): pass
        raise SystemExit('Relay startup timed out')
    try: state = json.loads((directory/'state.json').read_text())
    except (OSError,ValueError): raise SystemExit('No relay state in '+str(directory))
    if args.command == 'status':
        if not owned_process(state):
            state['running'] = False
            state['bridge_connected'] = False
        print(json.dumps(state,indent=2)); return
    if state.get('token') and owned_process(state):
        os.kill(state['pid'],signal.SIGTERM)
        print('Stopped relay '+str(state['pid']))
    else: print('No matching owned relay process; nothing stopped')

if __name__ == '__main__': main()
