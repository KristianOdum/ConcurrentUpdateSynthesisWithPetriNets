import sys
import networkx as nx
import flip
import json

def main(jsonPath, waypointNFAPath):
    init = nx.DiGraph()
    fin = nx.DiGraph()
    edges = []
    with open(jsonPath) as json_file:
        data = json.load(json_file)
        for i_edge in data['Initial_routing']:
            init.add_edge(str(i_edge[0]), str(i_edge[1]))
            add_edge(edges, str(i_edge[0]), str(i_edge[1]))
        for f_edge in data['Final_routing']:
            fin.add_edge(str(f_edge[0]), str(f_edge[1]))
            add_edge(edges, str(f_edge[0]), str(f_edge[1]))

        properties = data['Properties']
        reachbility = properties['Reachability']

        graph = flip.Graph(init, fin, srcs=[str(reachbility['startNode'])], dst=str(reachbility['finalNode']))

        graph.subpaths = calc_waypoint_subpaths(waypointNFAPath)

        order = flip.compute_sequence(graph)
        order.verify()

def calc_waypoint_subpaths(nfaPath):
    with open(nfaPath) as nfa_file:
        lines = nfa_file.read().splitlines()
        states = next(line for line in lines if line.split(":")[0] == "States").split(":")[1].split(",")
        initial_state = next(line for line in lines if line.split(":")[0] == "Initial state").split(":")[1]
        final_states = next(line for line in lines if line.split(":")[0] == "Final states").split(":")[1].split(",")
        actionsInput = next(line for line in lines if line.split(":")[0] == "Actions").split(":")[1].split(";")
        actions = []  # List of tuples of form: (from, to, label)
        for a in actionsInput:
            t = tuple(a.split(","))
            if t[1] != initial_state and t[0] not in final_states:
                actions.append(t)

        paths = []
        pathsFrom(initial_state, states, actions, final_states, [], paths)
        return paths

def pathsFrom(state, states, actions, final_states, current_used_labels, paths):
    for action in outgoing_actions(state, actions, current_used_labels):
        new_used_labels = current_used_labels[:]
        new_used_labels.append(action[2])
        newState = action[1]
        if(newState in final_states):
            paths.append(new_used_labels)
        pathsFrom(newState, states, actions, final_states, new_used_labels, paths)

def outgoing_actions(state, actions, used_labels):
    for a in actions:
        if a[0] == state and a[2] not in used_labels:
            yield a

def add_edge(lst, src, tgt):
    if((src, tgt) not in lst):
        lst.append((src, tgt))

if __name__ == '__main__':
    jsonPath = sys.argv[1]
    waypointNFAPath = sys.argv[2]
    main(jsonPath, waypointNFAPath)