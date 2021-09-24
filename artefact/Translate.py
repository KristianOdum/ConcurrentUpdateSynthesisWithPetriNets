#!/bin/python3
import utils.TestNets as TN
import utils.PNBuilder as PN
import utils.LtLBuilder as LTL
import utils.JsonBuilder as JB
import os

### Writes a json, then xml,q,tapn and then a ltl file for a Zoo Topo
def translate(indir, network, outdir):
    json = f"{indir}/{network}"
    tname = network[:-5]
    PN.make_pn(json, json, f"{outdir}_pn/{tname}")
    LTL.gml_ltl(json, json, f"{outdir}_ltl/{tname}")

def translate_json(indir, outdir):
    cnt = 0
    for f in os.listdir(indir):
        translate(indir + "/", f, outdir)
        cnt += 1
    print(f"Succesfully written {cnt} files.")


translate_json("data/zoo_json", "data/zoo")
translate_json("data/nested_json", "data/nested")
translate_json("data/synthethic_json", "data/synthethic")
