import utils.JsonNonDetBuilder as JB
import time,os
import shutil
import random

### Generates a problem and writes it as json
def generate(indir, network, outdir, scale):
    gml = indir + network + ".gml"
    json = f"{outdir}_json/{network}_{scale}.json"
    JB.jsonbuilder(gml, json, scale)

def generate_all(indir, outdir, scale):
    start = time.time()
    cnt = 0
    dir = f"{outdir}_json"
    if os.path.exists(dir):
        shutil.rmtree(dir)
    os.mkdir(dir)

    for f in os.listdir(indir):
        for i in range(1, scale):
            generate(indir + "/", f[:-4], outdir, i)
            cnt += 1
    print("Operation done in: {} seconds".format((str(time.time()-start))[:5]))
    print(f"Succesfully written {cnt} files.")


#generate("data/gml/", "TataNld", "data/zoo3", 4)
generate_all("data/gml", "data/zooND2", 5)
