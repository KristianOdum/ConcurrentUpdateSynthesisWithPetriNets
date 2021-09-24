#/bin/python3
import utils.TestNets as TN

path = "data/synthethic_json"
for i in range(100, 1020, 20):
    TN.generate_disjoint(i, path)

for i in range(100, 1020, 20):
    TN.generate_shared(i, 5, path)
    TN.generate_shared(i, 10, path)
    TN.generate_shared(i, int(i/2), path)

for i in range(100, 1020, 20):
    TN.generate_dependent(i, 5, path)
    TN.generate_dependent(i, 10, path)
    TN.generate_dependent(i, int(i/2), path)

