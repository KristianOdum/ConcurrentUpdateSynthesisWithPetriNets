import time, os
import json
from entities.Node import Node

def json_maker(ntype, count, init_route, final_route, n0, nn, wp, out_path):
    mydic = {}
    mydic["Initial_routing"] = init_route
    mydic["Final_routing"] = final_route
    mydic["Properties"] = {}
    mydic["Properties"]["Waypoint"] = {}
    mydic["Properties"]["Waypoint"]["startNode"] = n0
    mydic["Properties"]["Waypoint"]["finalNode"] = nn
    mydic["Properties"]["Waypoint"]["waypoint"] = wp
    mydic["Properties"]["LoopFreedom"] = {}
    mydic["Properties"]["LoopFreedom"]["startNode"] = n0
    mydic["Properties"]["Reachability"] = {}
    mydic["Properties"]["Reachability"]["startNode"] = n0
    mydic["Properties"]["Reachability"]["finalNode"] = nn

    
    myjsondic = json.dumps(mydic, indent=4)
    f = open(f"{out_path}/{ntype}_{count}.json", "w")
    f.write(myjsondic)
    f.close()

    print(f"JSON for {ntype} network of size {count} generated")


def generate_disjoint (count, out_path):
    #Generating initial and final nodes
    #also path configurations based on size
    acc = count
    count = (int((count - 3) / 4) + 1) * 4 + 3
    init_node = Node(0)
    final_node = Node(count-1)
    mid_node = Node(count-2)
    path_count = (int((count-3)/4) + 1) * 2 + 1
    node_path_count = (int((count-3)/4)) * 2 
    path1 = []
    path2 = []
    #Creating nodes for the 2 paths 

    for i in range(node_path_count):
        path1.append(Node(i+1))
        path2.append(Node(i+1+node_path_count))
    
    init_route = []
    final_route = []

    init_node.init_route = path1[0].id
    init_node.final_route = path2[0].id

    init_route.append([init_node.id,init_node.init_route])
    final_route.append([init_node.id,init_node.final_route])

    for i in range(int(node_path_count/2) -1):
        path1[i].init_route = path1[i+1].id
        init_route.append([path1[i].id, path1[i+1].id])
        path2[i].final_route = path2[i+1].id
        final_route.append([path2[i].id, path2[i+1].id])

    path1[int(node_path_count/2) -1].init_route = mid_node.id
    path2[int(node_path_count/2) -1].final_route = mid_node.id
    init_route.append([path1[int(node_path_count/2) -1].id,mid_node.id])
    final_route.append([path2[int(node_path_count/2) -1].id,mid_node.id])

    mid_node.init_route = path1[int(node_path_count/2)].id
    mid_node.final_route = path2[int(node_path_count/2)].id
    init_route.append([mid_node.id, path1[int(node_path_count/2)].id])
    final_route.append([mid_node.id, path2[int(node_path_count/2)].id])

    for i in range(int(node_path_count/2), node_path_count - 1):
        path1[i].init_route = path1[i+1].id
        path2[i].final_route = path2[i+1].id
        init_route.append([path1[i].id, path1[i+1].id])
        final_route.append([path2[i].id, path2[i+1].id])

    path1[-1].init_route = final_node.id
    path2[-1].final_route = final_node.id
    init_route.append([path1[-1].id, final_node.id])
    final_route.append([path2[-1].id, final_node.id])


    #Making a json file out of the routings
    wp = mid_node.id

    #making the json file
    json_maker("Disjoint", acc, init_route, final_route, init_node.id, final_node.id, wp, out_path)


def pospath(x):
    return [[x, x+2],[x+2, x+1],[x+1, x+3]]

def generate_dependent (count, wp_mod, out_path):
    #Generating initial and final nodes
    #also path configurations based on size
    acc = count
    count = (int((count-1)/3)) * 3 + 1
    series = int((count-1)/3)
    nodes = []
    init_node = Node(0)
    init_node.init_route = 1
    final_node = Node(count-1)
    nodes.append(init_node)
    wp = []
    for i in range(count-2):
        nodes.append(Node(i+1))
        nodes[-1].init_route = i+2
        if ((i+1) % wp_mod) == 0:
            wp += [i+1]
    nodes.append(final_node)
    
    init_route = []
    final_route = []

    for node in nodes[:-1]:
        init_route.append([node.id, node.init_route])

    for i in range(series):
        for t in pospath(i*3):
            node = next((x for x in nodes if x.id == t[0]), None)
            node.final_route = t[1]
        final_route.extend(pospath(i*3))
    
    # verified: waypoint = literally anything
    # non verified: waypoint = something after the reach..

    
    #making the json file
    json_maker(f"Dependent_{wp_mod}", acc, init_route, final_route, init_node.id, final_node.id, wp, out_path)

def generate_shared(count, wp_mod, out_path):
    #Generating initial and final nodes
    #also path configurations based on size
    acc = count
    count = (int((count-1)/3)) * 3 + 1
    common_count = int ((count - 4) / 3)
    common = []
    path_count = int ((count - 2 - common_count)/2)
    path1 = []
    path2 = []
    init_node = Node(0)
    final_node = Node(count-1)
    #Making the common nodes
    for i in range(common_count):
        common.append(Node(i+1))
    
    common.append(final_node)
    #Making the routings
    for i in range(path_count):
        path1.append(Node(i+path_count))
        path1[-1].init_route = common[i].id
        path2.append(Node(i+2*path_count))
        path2[-1].final_route = common[i].id

    wp = []
    for i in range(common_count):
        common[i].init_route = path1[i+1].id
        common[i].final_route = path2[i+1].id
        if (i != init_node.id and i != final_node.id) and (i % (wp_mod / 2)) == 0:
            wp += [i]

    init_node.init_route = path1[0].id
    init_node.final_route = path2[0].id

    #Making a json file out of the routings
    init_route = []
    final_route = []

    init_route.append([init_node.id, path1[0].id])
    final_route.append([init_node.id, path2[0].id])

    for i in range (path_count -1):
        init_route.append([path1[i].id, path1[i].init_route])
        init_route.append([path1[i].init_route, path1[i+1].id])
        final_route.append([path2[i].id, path2[i].final_route])
        final_route.append([path2[i].final_route, path2[i+1].id])

    init_route.append([path1[-1].id, final_node.id])
    final_route.append([path2[-1].id, final_node.id])

    json_maker(f"Shared_{wp_mod}", acc, init_route, final_route, init_node.id, final_node.id, wp, out_path)

