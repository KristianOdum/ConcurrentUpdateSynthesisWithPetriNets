import networkx as nx
import json, time
import random
import os


class Waypoint:

    def __init__(self, u, v, wp):
        self.startNode = u
        self.finalNode = v
        self.waypoint = wp


class LoopFreedom:

    def __init__(self, u):
        self.startNode = u


class Reachability:

    def __init__(self, u, v):
        self.startNode = u
        self.finalNode = v


def isDirected(filepath):
    with open(filepath, 'r') as info:
        for line in info:
            if line.strip() == "directed 1":
                return True
    return False


def makeDirected(filepath):
    with open(filepath, 'r+') as info:
        contents = info.readlines()
        contents.insert(2, "  directed 1\n")
        info.seek(0)
        info.writelines(contents)

def extend(path, existing_ids):
    mx = max(existing_ids)+1
    return path + [x + mx for x in path]


def jsonbuilder(filepath, outpath, scale=1):

    if not isDirected(filepath):
        makeDirected(filepath)

    start = time.time()
    g = nx.read_gml(filepath, label='id')
    g = nx.Graph(g)
    routings = []
    for i in range(0, 10):
        routings = findRoutings(g)
        if routings:
            break
    #findRoutingsV2(g)

    if routings:
        p1 = routings[0]
        p2 = routings[1]
        for i in range(1, scale):
            allid = set(p1 + p2)
            p1 = extend(p1, allid)
            p2 = extend(p2, allid)
        info = generateJSONInfo(g, [p1,p2])
        generateJSONFile(info, outpath)
        print("Success! Json settings for {} generated! Execution time: {} seconds".format(filepath,
                                                                                           (str(time.time() - start))[
                                                                                           :5]))
        if "Waypoint" in info["Properties"]:
            return True
        else:
            print("\t..but no waypoints, skip")
            return False
    else:
        print("Failure! No final routing available for {}... Execution time: {} seconds.".format(filepath,
                                                                                          (str(time.time() - start))[
                                                                                          :5]))
    return False



def findRoutings(g):
    print("Started Generating")
    length = 0
    routings = []
    n = 0
    tmp = [n for n in g.nodes()]
    for source in random.choices(tmp, k=min(len(tmp), 100)):
        for dest in random.choices(tmp, k=min(len(tmp), 100)):
            if source == dest:
                continue
            try:
                p1 = nx.dijkstra_path(g, source, dest)
                if len(p1) < length :
                    continue
                if len(p1) == length :
                    n = n + 1
                    if random.randint(0,n-1) != 0:
                        continue
                else:
                    n = 0
                length = len(p1)
                routings = p1
            except:
                continue
    first = routings[0]
    last = routings[-1]
    if len(routings) < 3:
        return False
    waypoint = random.choice(routings[1:-1])
    weights = {}
    for n in g.nodes():
        for m in g.nodes():
            weights[(n,m)] = random.randint(0,5)

    p1w = nx.dijkstra_path(g, first, waypoint, lambda x,y,z: weights[(x,y)])
    p1e = nx.dijkstra_path(g, waypoint, last, lambda x,y,z: weights[(x,y)])   
    p1 = p1w[:-1] + p1e
    for i in range(0,len(p1)-1):
        weights[(p1[i],p1[i+1])] *= 4
        if weights[(p1[i],p1[i+1])] > 20:
            weights[(p1[i],p1[i+1])] = 20

    p2w = nx.dijkstra_path(g, first, waypoint, lambda x,y,z: weights[(x,y)])
    p2e = nx.dijkstra_path(g, waypoint, last, lambda x,y,z: weights[(x,y)])   
    p2 = p2w[:-1] + p2e
    if len(set(p2)) != len(p2):
        return False
    if len(set(p1)) != len(p1):
        return False
    if p1 == p2:
        return False


    print(p1)
    print(p2)

    if random.randint(0,1) == 0:
        return [p1,p2]
    else:
        return [p2,p1]

# In progress...
def diff_factor(in0,in1):
    n = 0
    return n
def findRoutingsV2(g):
    nodes_raw = list(g.nodes())
    length = 0

    maxdiff = 0
    candidate = {}
    for source in nodes_raw:
        for target in nodes_raw:
            if source != target and nx.has_path(g,source,target):
                allpaths = list(nx.all_simple_paths(g,source,target))
                if len(allpaths) > 1:
                    candidate[source,target] = allpaths
    
    candidates = list(candidate.values())

    good_candidates = []
    for c in candidates:
        wps = []
        for i in c:
            wps.append(set(i[1::-1]))
        if len(set.intersection(*wps)) > 1:
            good_candidates.append(c)
    print("Candidates: ", candidates)
    print("Good candidates: ", good_candidates)    



def generateJSONFile(info, outpath):
    myjsondic = json.dumps(info, indent=4)
    f = open(outpath, "w")
    f.write(myjsondic)
    f.close()


def generateJSONInfo(g, routings: list):
    mydic = {}
    init_path = routings[0]
    final_path = routings[-1]
    source = init_path[0]
    target = init_path[-1]

    #print(f"Nodes in graph: {g.nodes}")
    #print(f"Edges in graph: {g.edges}")
    # print(f"Max shortest path: {lmax}, between {s} and {t}")
    #print(f"Initial Path: {str(init_path)}")
    # all_paths = list(nx.all_simple_paths(g, source=source, target=target))
    # print(f"Amount of paths: {len(all_paths)}")
    # print(f"Biggest path size: {lmax_path}")
    #print(f"Final Path: {str(final_path)}")
    s1 = set(init_path[1:-1])
    s2 = set(final_path[1:-1])
    wps = list(s1.intersection(s2))
    print(f"Common waypoints: {wps}")
    init_route = []
    final_route = []
    for node in range(len(init_path) - 1):
        init_route.append([init_path[node], init_path[node + 1]])
    for node in range(len(final_path) - 1):
        final_route.append([final_path[node], final_path[node + 1]])
    #print(f"Init routing: {init_route}")
    #print(f"Final routing: {final_route}")

    mydic["Initial_routing"] = init_route
    mydic["Final_routing"] = final_route
    mydic["Properties"] = {}
    if wps:
        mydic["Properties"]["Waypoint"] = Waypoint(source, target, wps[0]).__dict__
    mydic["Properties"]["LoopFreedom"] = LoopFreedom(source).__dict__
    mydic["Properties"]["Reachability"] = Reachability(source, target).__dict__
    return mydic


def build_all():
    not_converted = []
    start = time.time()
    for f in os.listdir("data/gml/"):
        try:
            jsonbuilder(f[:-4])
        except:
            not_converted += f"{f}\n"
            print(f"Failure! {f} not converted..")
            continue
    f = open("not yet supported.txt", "w")
    f.writelines(not_converted)
    f.close()
    print("Operation done in: {} seconds".format((str(time.time() - start))[:5]))
