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

def extend(path, maxid):
    mx = maxid + 1
    return path + [x + mx for x in path]


def removeStuffThatCannotBeParsed(filepath):
    with open(filepath, "r") as f:
        lines = f.readlines()
    with open(filepath, "w") as f:
        for line in lines:
            if not line.strip("\n").__contains__("geocode"):
                f.write(line)

def jsonbuilder(filepath, outpath, scale=1):

    if not isDirected(filepath):
        makeDirected(filepath)

    start = time.time()

    removeStuffThatCannotBeParsed(filepath)

    g = nx.read_gml(filepath, label='id')
    g = nx.Graph(g)
    routings = []
    for i in range(0, 100):
        routings = findRoutings(g)
        if routings:
            break

    if routings:
        p1paths = routings[0]
        p2paths = routings[1]

        for i in range(1, scale):
            maxid = 0
            for p1p in p1paths:
                for p in p1p:
                    if p > maxid:
                        maxid = p
            for p2p in p2paths:
                for p in p2p:
                    if p > maxid:
                        maxid = p

            for p in range(len(p1paths)):
                p1paths[p] = extend(p1paths[p], maxid)
            for p in range(len(p2paths)):
                p2paths[p] = extend(p2paths[p], maxid)


        info = generateJSONInfo(g, [p1paths,p2paths])
        generateJSONFile(info, outpath)
        print("Success! Json settings for {} generated! Execution time: {} seconds".format(filepath,
                                                                                           (str(time.time() - start))[
                                                                                           :5]))

    else:
        print("Failure! No final routing available for {}... Execution time: {} seconds.".format(filepath,
                                                                                          (str(time.time() - start))[
                                                                                          :5]))
    return False


def findRoutings(g):
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

    #waypoint = random.choice(routings[1:-1])

    for n in g.nodes():
        for m in g.nodes():
            if (n,m) in g.edges():
                g[n][m]['weight'] = random.randint(1,5)

    p1minwpaths = list(nx.all_shortest_paths(g, first, last, "weight"))

    updatedweights = []
    for p in p1minwpaths:
        for i in range(0,len(p)-1):
            if(updatedweights.__contains__((p[i],p[i+1]))):
                continue
            g[p[i]][p[i+1]]['weight'] *= 4
            if g[p[i]][p[i+1]]['weight'] > 20:
                g[p[i]][p[i+1]]['weight'] = 20
            updatedweights.append((p[i],p[i+1]))

    p2minwpaths = list(nx.all_shortest_paths(g, first, last, "weight"))

    if(len(p1minwpaths) < 2 and len(p2minwpaths) < 2):
        return False


    for p1 in p1minwpaths:
        for p2 in p2minwpaths:
            if p1 == p2:
                return False

    if random.randint(0,1) == 0:
        return [p1minwpaths,p2minwpaths]
    else:
        return [p2minwpaths,p1minwpaths]

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
    init_paths = routings[0]
    final_paths = routings[1]
    source = init_paths[0][0]
    target = init_paths[0][-1]

    #Change this
    wps = [source]

    init_route = []
    final_route = []
    for ipath in init_paths:
        for node in range(len(ipath) - 1):
            if not init_route.__contains__([ipath[node], ipath[node + 1]]):
                init_route.append([ipath[node], ipath[node + 1]])

    for fpath in final_paths:
        for node in range(len(fpath) - 1):
            if not final_route.__contains__([fpath[node], fpath[node + 1]]):
                final_route.append([fpath[node], fpath[node + 1]])

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
