#!/usr/bin/env python3
"""Independent PyModbus RTU peer. Only the explicit probe command writes."""
import argparse
import datetime
import json
import os
from pathlib import Path
import sys
import time

from pymodbus.client import ModbusSerialClient
from pymodbus.server import StartSerialServer
from pymodbus.simulator import DataType, SimData, SimDevice


def devices():
    return [SimDevice(unit, simdata=(
        [SimData(0, values=[bool(i % 2) for i in range(100)], datatype=DataType.BITS)],
        [SimData(0, values=[not bool(i % 2) for i in range(100)], datatype=DataType.BITS, readonly=True)],
        [SimData(0, values=[unit*1000+i for i in range(100)], datatype=DataType.REGISTERS)],
        [SimData(0, values=[unit*2000+i for i in range(100)], datatype=DataType.REGISTERS, readonly=True)],
    ), use_bit_addressing=True) for unit in (1,2)]


def probe(client, units, address, exception_address, broadcast):
    results = []
    def check(label, operation, expected=None, exception=None, prefix=None, minimum_count=None):
        started = time.monotonic()
        row = dict(name=label)
        try:
            response = operation()
            row.update(response=str(response), milliseconds=round((time.monotonic()-started)*1000,2))
            if exception is not None:
                row['passed'] = response.isError() and getattr(response,'exception_code',None) == exception
            else:
                row['passed'] = not response.isError()
                values = getattr(response,'registers',None) or getattr(response,'bits',None)
                if values is not None: row['values'] = values
                if expected is not None: row['passed'] &= values[:len(expected)] == expected
                if prefix is not None: row['passed'] &= values[:len(prefix)] == prefix
                if minimum_count is not None: row['passed'] &= values is not None and len(values) >= minimum_count
        except Exception as error:
            row.update(passed=False, error=str(error))
        results.append(row)
    for unit in units:
        kw = dict(device_id=unit)
        check(f'unit{unit}:FC01',lambda: client.read_coils(address,count=4,**kw),minimum_count=4)
        check(f'unit{unit}:FC02',lambda: client.read_discrete_inputs(address,count=4,**kw),minimum_count=4)
        check(f'unit{unit}:FC03',lambda: client.read_holding_registers(address,count=3,**kw),minimum_count=3)
        check(f'unit{unit}:FC04',lambda: client.read_input_registers(address,count=3,**kw),minimum_count=3)
        check(f'unit{unit}:FC05',lambda: client.write_coil(address,True,**kw))
        check(f'unit{unit}:FC05-readback',lambda: client.read_coils(address,count=1,**kw),[True])
        check(f'unit{unit}:FC06',lambda: client.write_register(address,1234,**kw))
        check(f'unit{unit}:FC06-readback',lambda: client.read_holding_registers(address,count=1,**kw),[1234])
        check(f'unit{unit}:FC15',lambda: client.write_coils(address,[False,True,True,False],**kw))
        check(f'unit{unit}:FC15-readback',lambda: client.read_coils(address,count=4,**kw),[False,True,True,False])
        check(f'unit{unit}:FC16',lambda: client.write_registers(address,[111,222,333],**kw))
        check(f'unit{unit}:FC16-readback',lambda: client.read_holding_registers(address,count=3,**kw),[111,222,333])
        check(f'unit{unit}:exception-illegal-address',lambda: client.read_holding_registers(exception_address,count=1,**kw),exception=2)
    if broadcast:
        # PyModbus generates the RTU frame; a broadcast has no response by definition.
        client.write_register(address,4321,device_id=0,no_response_expected=True)
        time.sleep(.1)
        for unit in units:
            check(f'unit{unit}:broadcast-readback',lambda unit=unit: client.read_holding_registers(address,count=1,device_id=unit),[4321])
    return dict(created_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                passed=all(row['passed'] for row in results), checks=results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=('server','read','probe'))
    parser.add_argument('--port')
    parser.add_argument('--state-dir',default='/tmp/modbus-rtu-lab')
    parser.add_argument('--side',choices=('A','B'),default='A')
    parser.add_argument('--units',default='1,2')
    parser.add_argument('--address',type=int,default=0)
    parser.add_argument('--exception-address',type=int,default=65000)
    parser.add_argument('--baudrate',type=int,default=19200)
    parser.add_argument('--timeout',type=float,default=1)
    parser.add_argument('--broadcast',action='store_true')
    parser.add_argument('--report')
    args=parser.parse_args()
    port=args.port or json.loads((Path(args.state_dir)/'state.json').read_text())['port_'+args.side.lower()]
    if args.command=='server':
        print(json.dumps(dict(role='server',port=port,units=[1,2],addresses='0..99',holding='unit*1000+address',input='unit*2000+address')),flush=True)
        StartSerialServer(devices(),port=port,baudrate=args.baudrate,timeout=args.timeout,
                          broadcast_enable=True,ignore_missing_devices=True)
        return
    client=ModbusSerialClient(port,baudrate=args.baudrate,timeout=args.timeout,retries=0)
    if not client.connect(): raise SystemExit('Cannot open '+port)
    try:
        units=[int(value) for value in args.units.split(',')]
        if args.command=='read':
            responses=[]
            for unit in units:
                response=client.read_holding_registers(args.address,count=3,device_id=unit)
                responses.append(dict(unit=unit,passed=not response.isError(),response=str(response),registers=getattr(response,'registers',None)))
            report=dict(passed=all(row['passed'] for row in responses),checks=responses)
        else:
            report=probe(client,units,args.address,args.exception_address,args.broadcast)
        text=json.dumps(report,indent=2)
        print(text)
        if args.report:
            fd=os.open(args.report,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
            os.fchmod(fd,0o600)
            with os.fdopen(fd,'w') as stream: stream.write(text+'\n')
        if not report['passed']: raise SystemExit(1)
    finally: client.close()

if __name__=='__main__': main()
