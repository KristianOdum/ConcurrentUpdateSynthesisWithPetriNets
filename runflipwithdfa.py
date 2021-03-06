import sys
import networkx as nx
import flip
import json

def main(jsonPath, subpathsPath):
    init = nx.DiGraph()
    fin = nx.DiGraph()
    with open(jsonPath) as json_file:
        data = json.load(json_file)
        for i_edge in data['Initial_routing']:
            init.add_edge(str(i_edge[0]), str(i_edge[1]))
        for f_edge in data['Final_routing']:
            fin.add_edge(str(f_edge[0]), str(f_edge[1]))

        properties = data['Properties']
        reachbility = properties['Reachability']

        graph = flip.Graph(init, fin, srcs=[str(reachbility['startNode'])], dst=str(reachbility['finalNode']))

        subpaths = open(subpathsPath).read().split(";")
        for sp in subpaths:
            l = []
            for i in sp.split(','):
                l.append(i)
            graph.subpaths.append(l)

        t0 = time.clock()
        order = flip.compute_sequence(graph)
        print 'Finished in ', time.clock() - t0, ' seconds'
        print order

        order.verify()

if __name__ == '__main__':
    jsonPath = sys.argv[1]
    subpathsPath = sys.argv[2]
    main(jsonPath, subpathsPath)
