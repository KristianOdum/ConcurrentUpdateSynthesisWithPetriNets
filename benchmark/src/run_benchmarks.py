import argparse
import os

argparser = argparse.ArgumentParser(description='Run update synthesis benchmarks.')

argparser.add_argument('benchmark_path', type=str, help='path to the root directory containing the test files.')
argparser.add_argument('translator_path', type=str, help='the path to the .jar for translation into petri game')
argparser.add_argument('-o', nargs=1, metavar='output_path', type=str, help='the output path of the raw data from the methods', default='output')

subparser = argparser.add_subparsers(help='Choose to run flip or ours')
flip_parser = subparser.add_parser('flip', help='Run benchmarks with flip')
flip_parser.add_argument('flip_path', type=str, help='path to the flip folder. This must contain runflipwithnfa.py')

ours_parser = subparser.add_parser('ours', help='Run benchmarks with ours')
ours_parser.add_argument('-e', '--engine', metavar='verifypn_games_path', type=str, help='the path to the verifypn-games engine')

argparser.add_argument('-s','--sbatch', metavar='sbatch arguments', type=str, help='arguments to pass to the sbatch call', default='')

args = argparser.parse_args()

if args.method == 'ours':
    error = False
    if args.engine is None:
        error = True
        argparser.error('when using method ours the path to verifypn-games engine must be specified (-e verifypn_games_path)')
    if args.translator is None:
        error = True
        argparser.error('when using method ours the path to translator must be specified (-t translator_path)')
    if error:
        exit(-1)

    def launchjob(fpath: str, args):
        print(f'sbatch {args.sbatch} "java -jar {args.translator} {args.engine} {fpath}"')

else:
    if args.flip is None:
        argparser.error('when using method flip the path to flip root must be specified (-f flip_path)')
        exit(-1)

for dir, dnames, fnames in os.walk(args.benchmark_path):
    for f in fnames:
        if f.endswith('.json'):
            launchjob(os.path.join(dir, f), args)

