#!/bin/python3
import utils.TestNets as TN
import utils.DTAPNBuilder as DB
import utils.CSVMaker as CM
import utils.LtLBuilder as LTL
import utils.JsonBuilder as JB
import time,os

def write_zoo(network):
    gml = "data/gml/" + network + ".gml"
    json = "data/zoo_json/" + network + ".json"
    JB.jsonbuilder(gml, json)

def write_all_to_file(scale):
    start = time.time()
    cnt = 0
    for f in os.listdir("data/gml"):
        write_zoo(f[:-4])
        cnt += 1
    print("Operation done in: {} seconds".format((str(time.time()-start))[:5]))
    print(f"Succesfully written {cnt} files.")


write_all_to_file(1)


