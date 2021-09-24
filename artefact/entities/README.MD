## ENTITIES Package

This package contains object representations of the different entities we can have in a Petri Net, such as Nodes, Transitions, and few types of Arcs.

- Each Node object will contain among other things an id, a marking, an outgoing transitions count and the id's of the nodes it has an outgoing connection to in the initial and final routing.
- Each Transition object will contain among other things an id, a source and a target, and the player.
- We have three types of arcs. 
    - `InboundArc` which is an arc going from a `source` to a `transition`, where the source is a place in the network. By specifing the `arctype` and `weight`, we can chose whether we want a normal arc or an inhibitor one, and we also specify the weight it bears.
    - `OutboundArc` is an arc going from a `transition` to a `target`.
    - `FullArc` is the combined version of the two arcs above. Given a source, a target and a transition, it will contain an Inbound arc from `source` to `transition` and an outbound one from `transition` to `target`.

> All the entities contain methods to parse their data into a `.tapn` accepted format that can be used when generating models for Tapaal. In the case of transitions, we also support shared ones in-object. As in a Tapaal network created by us there are exactly only two shared places, those places are created manually when building the network.