#!/bin/python3
import os
import networkx as nx
import random

def load_network(path):
    network = nx.read_gml(path, label='id')
    return nx.DiGraph(network)

def do_subnet(network, path, networks, out_path, out_name):
    net_in = {}
    net_out = {}
    subnet = nx.DiGraph()
    nxt = 0
    for n in network.nodes():
        sn = load_network(path + random.choice(networks))
        subnet = nx.operators.binary.disjoint_union(subnet, sn)
        nn = len(subnet.nodes())
        net_in[n] = []
        net_out[n] = []

        nxt = nn + 1
        for i in range(nxt, nn):
            if len(subnet.successors(n)) == 0:
                net_out += [i]
            if len(subnet.predecessors(n)) == 0:
                net_in += [i]

    for (src,dst) in network.edges():
        for o in net_out[src]:
            for i in net_in[dest]:
                subnet.add_edge(o, i)

    nx.write_gml(subnet, out_path + out_name)


def random_subnetting(path, out_path):
    networks = os.listdir(path)
    for f in networks:
        print(f)
        network = load_network(path + f)
        do_subnet(network, path, networks, out_path, f)


random.seed(42)
random_subnetting("data/gml/", "data/nested_gml/")

