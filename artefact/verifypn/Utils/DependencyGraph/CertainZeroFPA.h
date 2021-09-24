#ifndef CERTAINZEROFPA_H
#define CERTAINZEROFPA_H

#include "FixedPointAlgorithm.h"
#include "Edge.h"
#include "Configuration.h"
#include "../SearhStrategies.h"
#include "SearchStrategy/SearchStrategy.h"


namespace DependencyGraph {

class CertainZeroFPA : public FixedPointAlgorithm
{
public:
    CertainZeroFPA(Utils::SearchStrategies::Strategy type) : FixedPointAlgorithm(type)
    {
    }
    virtual ~CertainZeroFPA()
    {
    }
    virtual bool search(DependencyGraph::BasicDependencyGraph &t_graph) override;
protected:

    DependencyGraph::BasicDependencyGraph *graph;
    DependencyGraph::Configuration* vertex;
    
    void checkEdge(DependencyGraph::Edge* e, bool only_assign = false);
    void finalAssign(DependencyGraph::Configuration *c, DependencyGraph::Assignment a);
    void explore(DependencyGraph::Configuration *c);
    void addDependency(DependencyGraph::Edge *e,
                          DependencyGraph::Configuration *target);

};
}
#endif // CERTAINZEROFPA_H
