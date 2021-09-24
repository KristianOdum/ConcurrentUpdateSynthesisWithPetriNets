import utils.VarOps as varOps

def initialize_network(jsonParser):
    nodes = varOps.parse_nodes(jsonParser.unique_ids, "0")
    transitions = varOps.parse_transitions(jsonParser.full_route)
    
    for node in nodes:
        for t in transitions:
            if t.source == node.id:
                if [t.source, t.target] in jsonParser.init_route and [t.source, t.target] in jsonParser.final_route:
                    node.init_route = t.target
                    node.final_route = t.target
                elif [t.source, t.target] in jsonParser.init_route:
                    node.init_route = t.target
                elif [t.source, t.target] in jsonParser.final_route:
                    node.final_route = t.target
    return nodes, transitions

def get_node(node_id, nodes):
    return next((x for x in nodes if x.id == node_id), None)



