## UTILS Package

This package contains the different utility tools we built in order to run the experiments.

- `JsonBuilder.py`: Is used for the TopologyZoo part. It generates inital and final routings, as well as reach and waypoint constraints based on the GML file. If one wishes to alter the way initial and final routing is generated, the only function that has to be modified is `findRoutings(g)`.
- `JsonParser.py`: Is used to parse the generated JSON file for a TopologyZoo network. It also calls for the scaler from `VarOps.py` if we wish to "increase the magnitude".
- `VarOps.py`: Contains various operations, such as our JSON scaler. The way it works, if the initial routing for a network is ((4,5) (5,7)) and the final routing is (4,7), the scaler will "normalize" the nodes to incremental values starting from 0, using a dictionary. Thus 4 will get the key 0, 5 will get 1 and 7 2. If for example we ask for a scale of 1, the initial routing will become ((0,1) (1,2)) and the final will be (0,2). If we ask for a scale of 2 instead, the initial becomes ((0,1) (1,2) (2,3) (3,4)) and the final becomes ((0,2) (2,4)).

- `DTapnBuilder.py`: Contains the logic neede to generate DTapn XML and Q files, as well as tracking the time required to do so. There is a slight difference between generating the files for a TopologyZoo network or a synthethic one, but the difference lies only in the arguments passed, thus the underlieing logic is the same in both case.

- `TAPNBuilder.py`: Contains the logic needed to generate TAPN Tapaal files for the TopologyZoo.
- `BasicNetworkComponents.py`: this script is mainly used for generating TAPN files, more exactly initializing a network from the corresponding JSON file, building the `Routing` component and building also the `Switches`.
- `AdditionalNetworkComponents.py`: this script is used to generate the `Visited` components and also the Waypoint, LoopFreedom, Reachability and combined query.

- `LtlBuilder.py`: Contains the logic needed to create a LTL file from a JSON input.

> For one network, DTapnBuilder, TAPNBuilder and LtlBuilder are going to use the same JSON file when creating the files, thus our JSON files are kept "original" and the scaling is done at software level as mentioned above, so that we have consistent results and change is easier.

- `TestNets.py`: This contains all the logic needed to create synthethic Disjoint, Shared, and Worst(Fully dependent) case networks. Due to the repeating patterns of those three cases, some things were easier to implement in contrast to the TopologyZoo, and we are using simplified versions of some functions found in the files above where its needed. 

- We also have some "CSV makers", which are scripts that compile the results after running the experiemnts into convenient `.csv` files


