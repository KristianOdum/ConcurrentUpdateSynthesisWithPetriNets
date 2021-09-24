import json,os,csv
import time

## Switch class with 1 input and 2 outputs for dealing with Netsynth more easily
class Switch:
    def __init__(self, sid, out1 = None, out2 = None):
        self.sid = sid
        self.out1 = None
        self.out2 = None
        if out1:
            self.out1 = out1
        if out2:
            self.out2 = out2
    def info(self):
        if (self.out1 is not None and self.out2 is not None):
            return(f"""switch {self.sid} (in{self.sid}s,out{self.sid}s,out{self.sid}s_2) [] {{
 rule in{self.sid}s => out{self.sid}s []
 }}
 final {{
 rule in{self.sid}s => out{self.sid}s_2 []
 }}\n""")
        elif (self.out1 is not None):
            return(f"""switch {self.sid} (in{self.sid}s,out{self.sid}s) [] {{
 rule in{self.sid}s => out{self.sid}s []
 }}
 final {{
     
 }}\n""")

        elif (self.out2 is not None):
            return(f"""switch {self.sid} (in{self.sid}s,out{self.sid}s_2) [] {{

 }}
 final {{
 rule in{self.sid}s => out{self.sid}s_2 []
 }}\n""")
        else:
            return(f"""switch {self.sid} (in{self.sid}s,out{self.sid}s) [] {{
 rule in{self.sid}s => out{self.sid}s []
 }}
 final {{
 rule in{self.sid}s => out{self.sid}s []
 }}\n""")

    def links(self):
        if (self.out1 is not None and self.out2 is not None):
            return (f"link out{self.sid}s => in{self.out1}s []\n" + f"link out{self.sid}s_2 => in{self.out2}s []\n")
        elif self.out1 is not None:
            return f"link out{self.sid}s => in{self.out1}s []\n"
        elif self.out2 is not None:
            return f"link out{self.sid}s_2 => in{self.out2}s []\n"
        else:
            return None

def find_switch(switches, sid):
    r = next((x for x in switches if x.sid == sid), None)
    if r is None:
        print(f"could not find {sid}")
        exit(-1)
    return r


### This one is used for synthethic nets
def make_ltl(ntype,count,path="data/synthethic_json"):
    start = time.time()
    cnt_backup = count
    with open(f"data/synthethic_json/{ntype}_{count}.json") as f:
        data = json.load(f)

    count = data["Properties"]["Waypoint"]["finalNode"]+1
    switches = []

    for i in range(count):
        switches.append(Switch(i))
    
    init_route = data["Initial_routing"]
    final_route = data["Final_routing"]

    for edge in init_route:
        switch = find_switch(switches,edge[0])
        switch.out1 = edge[1]

    for edge in final_route:
        switch = find_switch(switches,edge[0])
        switch.out2 = edge[1]
    
    switch_init = find_switch(switches, data["Properties"]["Reachability"]["startNode"])
    ltl = ""
    for s in switches:
        ltl += s.info()
    
    ltl += f"link  => in{switch_init.sid}s []\n"

    for s in switches[:-1]:
        ltl += s.links()
    
    ltl += "spec\n"
    inn = data["Properties"]["Reachability"]["startNode"]
    outt = data["Properties"]["Reachability"]["finalNode"]
    wpp = data["Properties"]["Waypoint"]["waypoint"]
    
    init = f"port=in{inn}s"

    #x = outt
    #outt = wpp
    #wpp = x

    reach = f"!(port=out{outt}s)"
    wp = f"((port=in{wpp}s) & (TRUE U (port=out{outt}s)))"
    spec = f"{init} -> ({reach} U {wp})"

    ltl += spec
    #print(spec)
    #port=in0s ->    (! U ( & (U )
    #port=in0s ->    (!(port=out3s) U ((port=in1s) & (TRUE U (port=out3s)))

    #port=in0s ->    (!(port=out3s) U ((port=in1s) & (TRUE U (port=out3s))))
    f = open(f"data/synthethic_ltl/{ntype}_{cnt_backup}.ltl", "w")
    f.write(ltl)
    f.close()

    f = open(f"data/time/{ntype}/{ntype}_{cnt_backup}_LTL.txt", "w")
    f.write(str(time.time() - start))
    f.close()

    print(f"LTL for {ntype} network of size {cnt_backup} generated in {time.time()-start} seconds")


### This one is used for Zoo Topology
def gml_ltl(gml, config, out):
    start = time.time()
    with open(config) as f:
        data = json.load(f)
    sw = []
    for i in data["Initial_routing"]:
        sw.append(i[0])

    for i in data["Final_routing"]:
        sw.append(i[0])
    sw.append(data["Final_routing"][-1][1])

    swnodup = list(dict.fromkeys(sw))
    #print(swnodup)
    switches = []

    for i in swnodup:
        switches.append(Switch(i))
    
    init_route = data["Initial_routing"]
    final_route = data["Final_routing"]
    for edge in init_route:
        switch = find_switch(switches,edge[0])
        switch.out1 = edge[1]

    for edge in final_route:
        switch = find_switch(switches,edge[0])
        switch.out2 = edge[1]
    
    switch_init = find_switch(switches, data["Properties"]["Reachability"]["startNode"])
    ltl = ""
    for s in switches:
        ltl += s.info()
    
    ltl += f"link  => in{switch_init.sid}s []\n"
    
    for s in switches:
        links = s.links()
        if links is not None:

            ltl += links
    
    ltl += "spec\n"
    inn = data["Properties"]["Reachability"]["startNode"]
    outt = data["Properties"]["Reachability"]["finalNode"]
    wpp = data["Properties"]["Waypoint"]["waypoint"]
    
    init = f"port=in{inn}s"

    #x = outt
    #outt = wpp
    #wpp = x

    reach = f"!(port=out{outt}s)"
    wp = ""
    if isinstance(wpp, list):
        wp = " & ".join(f"( {reach} U (port=in{x}s) & (TRUE U (port=out{outt}s)))" for x in wpp)
    else:
        wp = f"({reach} U (port=in{wpp}s) & ( TRUE U (port=out{outt}s)))"
    spec = f"{init} -> {wp}"

    ltl += spec
    #print(spec)
    #port=in0s ->    (! U ( & (U )
    #port=in0s ->    (!(port=out3s) U ((port=in1s) & (TRUE U (port=out3s)))

    #port=in0s ->    (!(port=out3s) U ((port=in1s) & (TRUE U (port=out3s))))
    f = open(out + ".ltl", "w")
    f.write(ltl)
    f.close()

    print(f"LTL for {gml} network generated in {time.time()-start} seconds")
    return (len(data["Initial_routing"]) + len(data["Final_routing"]))
