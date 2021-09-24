#ifndef ONTHEFLYDG_H
#define ONTHEFLYDG_H

#include "Utils/DependencyGraph/BasicDependencyGraph.h"
#include "Utils/DependencyGraph/Configuration.h"
#include "Utils/DependencyGraph/Edge.h"

#include "Utils/Structures/ptrie_map.h"
#include "Utils/Structures/AlignedEncoder.h"
#include "Utils/Structures/linked_bucket.h"

#include "PetriParse/PNMLParser.h"

#include "PetriEngine/PQL/PQL.h"
#include "PetriEngine/ReducingSuccessorGenerator.h"

#include "PetriConfig.h"

#include <functional>

namespace PetriEngine {
using namespace PetriEngine::PQL;
class CTLDependencyGraph : public DependencyGraph::BasicDependencyGraph
{
public:
    CTLDependencyGraph(PetriEngine::PetriNet& t_net, const Condition_ptr& query, bool partial_order);

    virtual ~CTLDependencyGraph();

    //Dependency graph interface
    virtual std::vector<DependencyGraph::Edge*> successors(DependencyGraph::Configuration *c) override;
    virtual DependencyGraph::Configuration *initialConfiguration() override;
    virtual void cleanUp() override;
    void setQuery(const Condition_ptr& query);

    virtual void release(DependencyGraph::Edge* e) override;

    //stats
    size_t configurationCount() const;
    size_t markingCount() const;
    
    PetriEngine::PQL::Result initialEval();

protected:

    //initialized from constructor
    PetriEngine::PetriNet& net;
    AlignedEncoder encoder;
    PetriConfig* initial_config;
    MarkPtr working_marking;
    MarkPtr query_marking;
    uint32_t n_transitions = 0;
    uint32_t n_places = 0;
    size_t _markingCount = 0;
    size_t _configurationCount = 0;
    //used after query is set
    Condition_ptr query = nullptr;

    PetriEngine::PQL::Result fastEval(Condition* query, const MarkVal* unfolded);
    PetriEngine::PQL::Result fastEval(const Condition_ptr& query, const MarkVal* unfolded)
    {
        return fastEval(query.get(), unfolded);
    }
    void nextStates(const MarkVal* t_marking, Condition*,
    std::function<void ()> pre, 
    std::function<bool (const MarkVal*)> foreach, 
    std::function<void ()> post);
    template<typename T>
    void dowork(T& gen, bool& first, 
    std::function<void ()>& pre, 
    std::function<bool (const MarkVal*)>& foreach)
    {
        gen.prepare(query_marking.get());

        while(gen.next(working_marking.get())){
            if(first) pre();
            first = false;
            if(!foreach(working_marking.get()))
            {
                gen.reset();
                break;
            }
        }
    }
    PetriConfig *createConfiguration(size_t marking, Condition* query);
    PetriConfig *createConfiguration(size_t marking, const Condition_ptr& query)
    {
        return createConfiguration(marking, query.get());
    }
    size_t createMarking(const MarkVal* marking);
    void markingStats(const MarkVal* marking, size_t& sum, bool& allsame, uint32_t& val, uint32_t& active, uint32_t& last);
    
    DependencyGraph::Edge* newEdge(DependencyGraph::Configuration &t_source, uint32_t weight);

    std::stack<DependencyGraph::Edge*> recycle;
    ptrie::map<std::vector<PetriConfig*> > trie;
    linked_bucket_t<DependencyGraph::Edge,1024*10>* edge_alloc = nullptr;

    // Problem  with linked bucket and complex constructor
    linked_bucket_t<char[sizeof(PetriConfig)], 1024*1024>* conf_alloc = nullptr;
    
    PetriEngine::ReducingSuccessorGenerator _redgen;
    const bool _partial_order = false;

};


}
#endif // ONTHEFLYDG_H
