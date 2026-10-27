import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

import serial
from pymodbus.client import ModbusSerialClient

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from lab import WriteQueue, owned_process

class LabTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.processes=[]
    def tearDown(self):
        for process in reversed(self.processes):
            process.terminate()
            try: process.wait(timeout=3)
            except subprocess.TimeoutExpired: process.kill();process.wait()
            if process.stdout: process.stdout.close()
            if process.stderr: process.stderr.close()
        self.temp.cleanup()
    def relay(self,*options):
        directory=Path(self.temp.name)
        process=subprocess.Popen([sys.executable,str(ROOT/'lab.py'),'serve','--state-dir',str(directory),*options],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        self.processes.append(process)
        state=json.loads(process.stdout.readline())
        return state
    def ports(self,state):
        a=serial.Serial(state['port_a'],19200,timeout=.3)
        b=serial.Serial(state['port_b'],19200,timeout=.3)
        self.addCleanup(a.close);self.addCleanup(b.close)
        return a,b
    def test_pair_bidirectional(self):
        state=self.relay(); a,b=self.ports(state)
        a.write(b'\x01\x03\x00\xff'); self.assertEqual(b.read(4),b'\x01\x03\x00\xff')
        b.write(b'\x02\x04\x80'); self.assertEqual(a.read(3),b'\x02\x04\x80')
        self.assertEqual((Path(self.temp.name)/'traffic.jsonl').stat().st_mode & 0o777,0o600)
    def test_chunk_delay_echo_and_corrupt(self):
        state=self.relay('--chunk-size','1','--chunk-gap-ms','2','--delay-ms','20','--echo','--corrupt-every','1')
        a,b=self.ports(state); started=time.monotonic()
        a.write(b'\x01\x02\x03');self.assertEqual(a.read(3),b'\x01\x02\x03')
        self.assertEqual(b.read(3),b'\x01\x02\x02');self.assertGreater(time.monotonic()-started,.02)
    def test_adjacent_frames_keep_order_when_chunks_overlap(self):
        state=self.relay('--chunk-size','1','--chunk-gap-ms','40')
        a,b=self.ports(state)
        b.timeout=1
        a.write(b'abcdef')
        time.sleep(.025)  # Second input frame arrives before first output drains.
        a.write(b'GHIJKL')
        self.assertEqual(b.read(12),b'abcdefGHIJKL')

    def test_drop(self):
        state=self.relay('--drop-every','2');a,b=self.ports(state)
        a.write(b'first');self.assertEqual(b.read(5),b'first')
        a.write(b'second');self.assertEqual(b.read(6),b'')
    def test_tcp_bridge(self):
        listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen();listener.settimeout(2)
        self.addCleanup(listener.close)
        state=self.relay('--bridge-port',str(listener.getsockname()[1]));connection,_=listener.accept();self.addCleanup(connection.close);connection.settimeout(2)
        a=serial.Serial(state['port_a'],19200,timeout=1);self.addCleanup(a.close)
        connection.sendall(b'android');self.assertEqual(a.read(7),b'android')
        a.write(b'host');self.assertEqual(connection.recv(4),b'host')
    def test_independent_pymodbus_peer_all_functions(self):
        state=self.relay()
        server=subprocess.Popen([sys.executable,str(ROOT/'peer.py'),'server','--port',state['port_b']],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        self.processes.append(server)
        self.assertIn('server',server.stdout.readline());time.sleep(.3)
        client=ModbusSerialClient(state['port_a'],baudrate=19200,timeout=.5,retries=0)
        self.assertTrue(client.connect());self.addCleanup(client.close)
        response=client.read_holding_registers(0,count=3,device_id=1)
        self.assertEqual(response.registers,[1000,1001,1002])
        sys.path.insert(0,str(ROOT))
        from peer import probe
        report=probe(client,[1,2],0,65000,True)
        self.assertTrue(report['passed'],json.dumps(report,indent=2))

class WriteQueueTests(unittest.TestCase):
    def test_partial_and_eagain_preserve_head_for_all_destinations(self):
        # Both PTY os.write and socket.send use this exact queue implementation.
        for destination in ('PTY master', 'bridge PTY slave', 'TCP socket'):
            with self.subTest(destination=destination):
                output=bytearray()
                actions=iter([2,BlockingIOError(),1,BlockingIOError(),2,99])
                progress=[]
                def writer(data):
                    action=next(actions)
                    if isinstance(action,Exception):raise action
                    size=min(action,len(data));output.extend(data[:size]);return size
                queue=WriteQueue(writer,progress.append)
                queue.append(b'abcd',0)
                queue.append(b'EFGH',0)
                queue.flush(1)
                self.assertEqual(output,b'ab')
                queue.flush(1)
                self.assertEqual(output,b'abc')
                queue.flush(1)
                self.assertEqual(output,b'abcdEFGH')
                self.assertFalse(queue.items)
                self.assertEqual(sum(progress),8)

    def test_next_frame_starts_after_previous_scheduled_tail(self):
        output=bytearray()
        queue=WriteQueue(lambda data: output.extend(data) or len(data))
        queue.append_frame(b'abc',0,0,1,.1)
        queue.append_frame(b'DEF',.02,0,1,.1)
        queue.flush(.02);self.assertEqual(output,b'a')
        queue.flush(.21);self.assertEqual(output,b'abc')
        queue.flush(.51);self.assertEqual(output,b'abcDEF')

class OwnershipAndProbeTests(unittest.TestCase):
    @patch('lab.subprocess.run')
    def test_pid_reuse_does_not_claim_unrelated_process(self, run):
        run.return_value.stdout='python unrelated.py'
        self.assertFalse(owned_process(dict(pid=123,running=True,token='unique')))
        run.return_value.stdout=f'python {ROOT / "lab.py"} serve --token different'
        self.assertFalse(owned_process(dict(pid=123,running=True,token='unique')))
        run.return_value.stdout=f'python {ROOT / "lab.py"} serve --token unique'
        self.assertTrue(owned_process(dict(pid=123,running=True,token='unique')))

    def test_probe_rejects_short_successful_read_responses(self):
        from peer import probe
        response=Mock(registers=[],bits=[])
        response.isError.return_value=False
        client=Mock()
        for method in ('read_coils','read_discrete_inputs','read_holding_registers','read_input_registers',
                       'write_coil','write_register','write_coils','write_registers'):
            getattr(client,method).return_value=response
        report=probe(client,[1],0,65000,False)
        for function in ('FC01','FC02','FC03','FC04'):
            row=next(row for row in report['checks'] if row['name']==f'unit1:{function}')
            self.assertFalse(row['passed'])

if __name__=='__main__':unittest.main()
