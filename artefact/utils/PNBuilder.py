import json, time
from utils.JsonParser import JsonParser
import utils.BasicNetworkComponents as BNC

def make_place(name, init = 0):
    return f"<place id=\"{name}\" >\n<name><text>{name}</text></name>\n<initialMarking><text>{init}</text></initialMarking>\n</place>\n"

def make_transition(name, player = 0):
    return f"<transition id=\"{name}\">\n<player><value>{player}</value></player><name><text>{name}</text></name></transition>\n"

def make_input(src, dest, weight = 1):
    return f"<arc id=\"{src}_{dest}\" source=\"{src}\" target=\"{dest}\" type=\"normal\">\n<inscription><text>{weight}</text></inscription></arc>\n"

def make_output(src, dest, weight = 1):
    return make_input(src, dest, weight)

def make_inhib(src, dest, weight = 1):
    return f"<arc id=\"{src}_{dest}\" source=\"{src}\" target=\"{dest}\" weight=\"{weight}\" type=\"inhibitor\" >\n<inscription><text>{weight}</text></inscription></arc>\n"



## This one is used for Zoo Topology nets
def build_composed_model_zoo(gml, json, out, places, transitions, json_params):
    start = time.time()

    switches = []
    ctrl = "Controller"
    inject = "Inject_packet"
    
    xml = ""
    xml += "<pnml>\n"
    xml += "<net id=\"ComposedModel\" type=\"P/T net\">"
    xml += "<page id=\"page0\">\n"

    ## Places part
    xml += make_place(f"{ctrl}", 1)
    targets = set()
    all_ids = set()
    for place in places:
        xml += make_place(f"P{place.id}", 0)
        if place.init_route is not None:
            targets.add(place.init_route)
        if place.final_route is not None:
            targets.add(place.final_route)
        all_ids.add(place.id)
    dff = all_ids.difference(targets)
    if len(dff) == 0:
        print("### PNML FAILED FOR {gml}, no initial ###")
        return
    initial = next(iter(dff))
    for place in places:
        if place.init_route is not None and place.final_route is not None:
            if place.init_route != place.final_route:
                xml += make_place(f"P{place.id}_initial", 1)
                xml += make_place(f"P{place.id}_final", 0)
                switches.append(place)
    for place in places:
        xml += make_place(f"P{place.id}_visited", 0)

    ## Transitions part
    for t in transitions:
        xml += make_transition(f"T{t.source}_{t.target}", 0)
    xml += make_transition(f"{inject}", 1)
    for t in switches:
        xml += make_transition(f"Update_{t.id}", 0)

    ## Input arcs part
    for t in transitions:
        xml += make_input(f"P{t.source}", f"T{t.source}_{t.target}")
    xml += make_input(f"{ctrl}", f"{inject}")
    for t in switches:
        xml += make_input(f"{ctrl}", f"Update_{t.id}")
        xml += make_input(f"P{t.id}_initial", f"Update_{t.id}")
        xml += make_input(f"P{t.id}_initial", f"T{t.id}_{t.init_route}")
        xml += make_input(f"P{t.id}_final", f"T{t.id}_{t.final_route}")

    ## Output arcs part
    for t in transitions:
        xml += make_output(f"T{t.source}_{t.target}", f"P{t.target}")
    xml += make_output(f"{inject}", f"P{initial}")
    xml += make_output(f"{inject}", f"P{initial}_visited")

    for t in switches:
        xml += make_output(f"Update_{t.id}", f"{ctrl}")
        xml += make_output(f"Update_{t.id}", f"P{t.id}_final")
        xml += make_output(f"T{t.id}_{t.init_route}", f"P{t.id}_initial")
        xml += make_output(f"T{t.id}_{t.final_route}", f"P{t.id}_final")
    for place in places:
        for t in transitions:
            if t.target == place.id:
                xml += make_output(f"T{t.source}_{t.target}", f"P{place.id}_visited")

    ## Inhibitor arcs part
    for place in places:
        if place.init_route == place.final_route:
            if place.init_route == None:
                continue
            xml += make_inhib(f"P{place.id}_visited", f"T{place.id}_{place.init_route}", 2)
        else:
            if place.init_route is not None:
                xml += make_inhib(f"P{place.id}_visited", f"T{place.id}_{place.init_route}", 2)
            if place.final_route is not None:
                xml += make_inhib(f"P{place.id}_visited", f"T{place.id}_{place.final_route}", 2)
    
    xml += "</page>"
    xml += "</net>\n"
    xml += "</pnml>"
    f = open(out + ".pnml", "w")
    f.write(xml)
    f.close

    print(f"PNML for {gml} network generated in {time.time()-start} seconds")

    start = time.time()
    reach = json_params["Properties"]["Reachability"]["finalNode"]
    waypoint = json_params["Properties"]["Waypoint"]["waypoint"]

    f = open(out + ".q", "w")
    f.write(build_query(reach=reach, waypoint=waypoint))
    f.close


## This is one is used by both
def build_query(ntype=None, reach=None, waypoint=None, negative=False):
    if negative:
        x = reach
        reach = waypoint
        waypoint = x
    query = ""
    wp = ""
    if isinstance(waypoint, list): 
        wp = " and ".join(f" (P{x}_visited >= 1 or P{reach}_visited = 0) " for x in waypoint)
    else:
        wp = f"(P{waypoint}_visited >= 1 or P{reach}_visited = 0)"
    q = f"AG ((!(deadlock) or P{reach}_visited >= 1) and {wp})"
    query += q
    return query

def make_pn(gml, json, out):
    start = time.time()
    jsonParser = JsonParser(json)
    nodes, transitions = BNC.initialize_network(jsonParser)
    build_composed_model_zoo(gml, json, out, nodes, transitions, jsonParser.data)
