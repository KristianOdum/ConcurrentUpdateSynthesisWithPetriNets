#! /usr/bin/python3
import argparse
import os

argparser = argparse.ArgumentParser(description='Run update synthesis benchmarks.')

argparser.add_argument('test_path', type=str, help='path to the test json.')
argparser.add_argument('translator_path', type=str, help='the path to the .jar for translation into petri game')
argparser.add_argument('-o', nargs=1, metavar='output_path', type=str, help='the output path of the raw data from the methods', default='output')

subparser = argparser.add_subparsers(title='method', dest='method', help='Choose to run flip or ours')
flip_parser = subparser.add_parser('flip', help='Run benchmarks with flip')
flip_parser.add_argument('flip_path', type=str, help='path to the flip folder. This must contain runflipwithnfa.py')

ours_parser = subparser.add_parser('ours', help='Run benchmarks with ours')
ours_parser.add_argument('-e', '--engine', metavar='verifypn_games_path', type=str, help='the path to the verifypn-games engine')

args = argparser.parse_args()

if args.method == 'ours':
    def launchjob(fpath: str, args):
        os.system(f'java -jar {args.translator} {args.engine} {fpath}')

else:
    def launchjob(fpath: str, args):
        os.system(f'{args.flip_path}/runflipwithnfa.py {args.translator}  {fpath}"')