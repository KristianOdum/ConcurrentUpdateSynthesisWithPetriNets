#include "CTLDependencyGraph.h"

#include <algorithm>
#include <string.h>
#include <iostream>
#include <memory>
#include <queue>
#include <limits>

#include "PetriEngine/SuccessorGenerator.h"
#include "PetriEngine/PQL/PQL.h"
#include "PetriEngine/PQL/Expressions.h"
#include "Utils/DependencyGraph/SearchStrategy/SearchStrategy.h"


using namespace PetriEngine::PQL;
using namespace DependencyGraph;

namespace PetriEngine {

CTLDependencyGraph::CTLDependencyGraph(PetriEngine::PetriNet& t_net, const Condition_ptr& query, bool partial_order) 
: 
    net(t_net),
    encoder(net.numberOfPlaces(), 0), 
    n_places(net.numberOfPlaces()),
    n_transitions(net.numberOfTransitions()),
    edge_alloc(new linked_bucket_t<DependencyGraph::Edge,1024*10>(1)), 
    conf_alloc(new linked_bucket_t<char[sizeof(PetriConfig)], 1024*1024>(1)),
    _redgen(net, false, partial_order /*only do these computations if POR is enabled*/), 
    _partial_order(partial_order) 
{
    setQuery(query);
}


CTLDependencyGraph::~CTLDependencyGraph()
{
    cleanUp();
    //Note: initial marking is in the markings set, therefore it will be deleted by the for loop
    //TODO: Ensure we don't leak memory here, when code moving is done
    size_t s = conf_alloc->size();
    for(size_t i = 0; i < s; ++i)
    {
        ((PetriConfig*)&(*conf_alloc)[i])->~PetriConfig();
    }
    delete conf_alloc;
    delete edge_alloc;
}

void CTLDependencyGraph::setQuery(const Condition_ptr& query)
{
    this->query = query;
    working_marking = nullptr;
    query_marking = nullptr;
    initialConfiguration();
    assert(this->query);
}

Result CTLDependencyGraph::initialEval()
{
    initialConfiguration();
    EvaluationContext e(query_marking.get(), &net);
    return query->evaluate(e).first;
}

Configuration* CTLDependencyGraph::initialConfiguration()
{
    if(working_marking == nullptr)
    {
        working_marking = net.makeInitialMarking();
        query_marking = net.makeInitialMarking();
        initial_config = createConfiguration(createMarking(working_marking.get()), this->query);
    }
    return initial_config;
}

Result CTLDependencyGraph::fastEval(Condition* query, const MarkVal* unfolded)
{
    EvaluationContext e(unfolded, &net);
    return query->evaluate(e).first;
}


std::vector<DependencyGraph::Edge*> CTLDependencyGraph::successors(Configuration *c)
{
    //PetriEngine::PQL::DistanceContext context(&net, query_marking.get());
    PetriConfig *v = static_cast<PetriConfig*>(c);
    trie.unpack(v->marking, encoder.scratchpad().raw());
    encoder.decode(query_marking.get(), encoder.scratchpad().raw());
    //    v->printConfiguration();
    std::vector<Edge*> succs;
    auto query_type = v->query->getQueryType();
    if(query_type == EVAL){
        assert(false);
        //assert(false && "Someone told me, this was a bad place to be.");
        if (fastEval(query, query_marking.get()) == RTRUE){
            succs.push_back(newEdge(*v, 0));///*v->query->distance(context))*/0);
        }
    }
    else if (query_type == LOPERATOR){
        if(v->query->getQuantifier() == NEG){
            // no need to try to evaluate here -- this is already transient in other evaluations.
            auto cond = static_cast<NotCondition*>(v->query);
            Configuration* c = createConfiguration(v->marking, (*cond)[0]);
            Edge* e = newEdge(*v, /*v->query->distance(context)*/0);
            e->is_negated = true;
            e->addTarget(c);
            succs.push_back(e);
        }
        else if(v->query->getQuantifier() == AND){
            auto cond = static_cast<AndCondition*>(v->query);
            //Check if left is false
            std::vector<Condition*> conds;
            for(auto& c : *cond)
            {
                auto res = fastEval(c.get(), query_marking.get());
                if(res == RFALSE)
                {
                    return succs;
                }
                if(res == RUNKNOWN)
                {
                    conds.push_back(c.get());
                }
            }
            
            Edge *e = newEdge(*v, /*cond->distance(context)*/0);

            //If we get here, then either both propositions are true (shouldn't be possible)
            //Or a temporal operator and a true proposition
            //Or both are temporal
            for(auto c : conds)
            {
                assert(c->isTemporal());
                e->addTarget(createConfiguration(v->marking, c));
            }
            succs.push_back(e);
        }
        else if(v->query->getQuantifier() == OR){
            auto cond = static_cast<OrCondition*>(v->query);
            //Check if left is true
            std::vector<Condition*> conds;
            for(auto& c : *cond)
            {
                auto res = fastEval(c.get(), query_marking.get());
                if(res == RTRUE)
                {
                    succs.push_back(newEdge(*v, 0));
                    return succs;
                }
                if(res == RUNKNOWN)
                {
                    conds.push_back(c.get());
                }
            }

            //If we get here, either both propositions are false
            //Or one is false and one is temporal
            //Or both temporal
            for(auto c : conds)
            {
                assert(c->isTemporal());
                Edge *e = newEdge(*v, /*cond->distance(context)*/0);
                e->addTarget(createConfiguration(v->marking, c));
                succs.push_back(e);
            }
        }
        else{
            assert(false && "An unknown error occoured in the loperator-part of the successor generator");
        }
    }
    else if (query_type == PATHQEURY){
        if(v->query->getQuantifier() == A){
            if (v->query->getPath() == U){
                auto cond = static_cast<AUCondition*>(v->query);
                Edge *right = nullptr;       
                auto r1 = fastEval((*cond)[1], query_marking.get());
                if (r1 != RUNKNOWN){
                    //right side is not temporal, eval it right now!
                    if (r1 == RTRUE) {    //satisfied, no need to go through successors
                        succs.push_back(newEdge(*v, 0));
                        return succs;
                    }//else: It's not valid, no need to add any edge, just add successors
                }
                else {
                    //right side is temporal, we need to evaluate it as normal
                    Configuration* c = createConfiguration(v->marking, (*cond)[1]);
                    right = newEdge(*v, /*(*cond)[1]->distance(context)*/0);
                    right->addTarget(c);
                }
                bool valid = false;
                Configuration *left = nullptr;
                auto r0 = fastEval((*cond)[0], query_marking.get());
                if (r0 != RUNKNOWN) {
                    //left side is not temporal, eval it right now!
                    valid = r0 == RTRUE;
                } else {
                    //left side is temporal, include it in the edge
                    left = createConfiguration(v->marking, (*cond)[0]);
                }
                if (valid || left != nullptr) {
                    //if left side is guaranteed to be not satisfied, skip successor generation
                    Edge* leftEdge = nullptr;
                    nextStates (query_marking.get(), cond,
                                [&](){ leftEdge = newEdge(*v, std::numeric_limits<uint32_t>::max());},
                                [&](const MarkVal* mark){
                                    auto res = fastEval(cond, mark);
                                    if(res == RTRUE) return true;
                                    if(res == RFALSE)
                                    {
                                        left = nullptr;
                                        leftEdge->targets.clear();
                                        leftEdge = nullptr;
                                        return false;
                                    }
                                    //context.setMarking(mark);
                                    Configuration* c = createConfiguration(createMarking(mark), cond);
                                    leftEdge->addTarget(c);
                                    return true;
                                },
                                [&]()
                                {
                                    if(leftEdge)
                                    {
                                        if (left != nullptr) {
                                            leftEdge->addTarget(left);
                                        }
                                        succs.push_back(leftEdge);                                    
                                    }
                                }
                            );
                } //else: Left side is not temporal and it's false, no way to succeed there...

                if (right != nullptr) {
                    succs.push_back(right);
                }
            }
            else if(v->query->getPath() == F){
                auto cond = static_cast<AFCondition*>(v->query);
                Edge *subquery = nullptr;
                auto r = fastEval((*cond)[0], query_marking.get());
                if (r != RUNKNOWN) {
                    bool valid = r == RTRUE;
                    if (valid) {
                        succs.push_back(newEdge(*v, 0));
                        return succs;
                    }
                } else {
                    subquery = newEdge(*v, /*cond->distance(context)*/0);
                    Configuration* c = createConfiguration(v->marking, (*cond)[0]);
                    subquery->addTarget(c);
                }
                Edge* e1 = nullptr;
                nextStates(query_marking.get(), cond,
                        [&](){e1 = newEdge(*v, std::numeric_limits<uint32_t>::max());},
                        [&](const MarkVal* mark)
                        {
                            auto res = fastEval(cond, mark);
                            if(res == RTRUE) return true;
                            if(res == RFALSE)
                            {
                                if(subquery)
                                {
                                    --subquery->refcnt;
                                    release(subquery);
                                    subquery = nullptr;
                                }
                                e1->targets.clear();
                                return false;
                            }
                            //context.setMarking(mark);
                            Configuration* c = createConfiguration(createMarking(mark), cond);
                            e1->addTarget(c);
                            return true;
                        },
                        [&]()
                        {
                            succs.push_back(e1);
                        }                    
                );

                if (subquery != nullptr) {
                    succs.push_back(subquery);
                }
            }
            else if(v->query->getPath() == X){
                auto cond = static_cast<AXCondition*>(v->query);
                Edge* e = newEdge(*v, std::numeric_limits<uint32_t>::max());
                Result allValid = RTRUE;
                nextStates(query_marking.get(), cond,
                        [](){}, 
                        [&](const MarkVal* mark){
                            auto res = fastEval((*cond)[0], mark);
                            if(res != RUNKNOWN)
                            {
                                if (res == RFALSE) {
                                    allValid = RFALSE;
                                    return false;
                                }
                            }
                            else
                            {
                                allValid = RUNKNOWN;
                                //context.setMarking(mark);
                                Configuration* c = createConfiguration(createMarking(mark), (*cond)[0]);
                                e->addTarget(c);
                            }
                            return true;
                        }, 
                        [](){}
                    );
                    if(allValid == RUNKNOWN)
                    {
                        succs.push_back(e);
                    }
                    else if(allValid == RTRUE)
                    {
                        e->targets.clear();
                        succs.push_back(e);
                    }
                    else
                    {
                    }
            }
            else if(v->query->getPath() == G ){
                assert(false && "Path operator G had not been translated - Parse error detected in succ()");
            }
            else
                assert(false && "An unknown error occoured in the successor generator");
        }
        else if(v->query->getQuantifier() == E){
            if (v->query->getPath() == U){
                auto cond = static_cast<EUCondition*>(v->query);
                Edge *right = nullptr;
                auto r1 = fastEval((*cond)[1], query_marking.get());
                if (r1 == RUNKNOWN) {
                    Configuration* c = createConfiguration(v->marking, (*cond)[1]);
                    right = newEdge(*v, /*(*cond)[1]->distance(context)*/0);
                    right->addTarget(c);
                } else {
                    bool valid = r1 == RTRUE;
                    if (valid) {
                        succs.push_back(newEdge(*v, 0));
                        return succs;
                    }   // else: right condition is not satisfied, no need to add an edge
                }


                Configuration *left = nullptr;
                bool valid = false;
                nextStates(query_marking.get(), cond,
                    [&](){
                        auto r0 = fastEval((*cond)[0], query_marking.get());
                        if (r0 == RUNKNOWN) {
                            left = createConfiguration(v->marking, (*cond)[0]);
                        } else {
                            valid = r0 == RTRUE;
                        }                        
                    },
                    [&](const MarkVal* marking){
                        if(left == nullptr && !valid) return false;
                        auto res = fastEval(cond, marking);
                        if(res == RFALSE) return true;
                        if(res == RTRUE)
                        {
                            for(auto s : succs){ --s->refcnt; release(s);};
                            succs.clear();
                            succs.push_back(newEdge(*v, 0));    
                            if(right)
                            {
                                --right->refcnt;
                                release(right);
                                right = nullptr;
                            }
                            
                            if(left)
                            {
                                succs.back()->addTarget(left);                                
                            }
                            
                            return false;
                        }
                        //context.setMarking(marking);
                        Edge* e = newEdge(*v, /*cond->distance(context)*/0);
                        Configuration* c1 = createConfiguration(createMarking(marking), cond);
                        e->addTarget(c1);
                        if (left != nullptr) {
                            e->addTarget(left);
                        }
                        succs.push_back(e);
                        return true;
                }, [](){});

                if (right != nullptr) {
                    succs.push_back(right);
                }
            }
            else if(v->query->getPath() == F){
                auto cond = static_cast<EFCondition*>(v->query);
                Edge *subquery = nullptr;
                auto r = fastEval((*cond)[0], query_marking.get());
                if (r != RUNKNOWN) {
                    bool valid = r == RTRUE;
                    if (valid) {
                        succs.push_back(newEdge(*v, 0));
                        return succs;
                    }
                } else {
                    Configuration* c = createConfiguration(v->marking, (*cond)[0]);
                    subquery = newEdge(*v, /*cond->distance(context)*/0);
                    subquery->addTarget(c);
                }

                nextStates(query_marking.get(), cond,
                            [](){},
                            [&](const MarkVal* mark){
                                auto res = fastEval(cond, mark);
                                if(res == RFALSE) return true;
                                if(res == RTRUE)
                                {
                                    for(auto s : succs){ --s->refcnt; release(s);};
                                    succs.clear();
                                    succs.push_back(newEdge(*v, 0));
                                    if(subquery)
                                    {
                                        --subquery->refcnt;
                                        release(subquery);
                                    }
                                    subquery = nullptr;
                                    return false;
                                }
                                //context.setMarking(mark);
                                Edge* e = newEdge(*v, /*cond->distance(context)*/0);
                                Configuration* c = createConfiguration(createMarking(mark), cond);
                                e->addTarget(c);
                                succs.push_back(e);
                                return true;
                            },
                            [](){}
                        );

                if (subquery != nullptr) {
                    succs.push_back(subquery);
                }
            }
            else if(v->query->getPath() == X){
                auto cond = static_cast<EXCondition*>(v->query);
                auto query = (*cond)[0];
                nextStates(query_marking.get(), cond,
                        [](){}, 
                        [&](const MarkVal* marking) {
                            auto res = fastEval(query, marking);
                            if(res == RTRUE)
                            {
                                for(auto s : succs){ --s->refcnt; release(s);};
                                succs.clear();
                                succs.push_back(newEdge(*v, 0));
                                return false;
                            }   //else: It can't hold there, no need to create an edge
                            else if(res == RUNKNOWN)
                            {
                                //context.setMarking(marking.marking());
                                Edge* e = newEdge(*v, /*(*cond)[0]->distance(context)*/0);
                                Configuration* c = createConfiguration(createMarking(marking), query);
                                e->addTarget(c);
                                succs.push_back(e);
                            }
                            return true;
                        },
                        [](){}
                    );
            }
            else if(v->query->getPath() == G ){
                assert(false && "Path operator G had not been translated - Parse error detected in succ()");
            }
            else
                assert(false && "An unknown error occoured in the successor generator");
        }
    }
    else
    {
        assert(false && "Should never happen");
    }
    return succs;
}

void CTLDependencyGraph::nextStates(const MarkVal* t_marking, Condition* ptr,
    std::function<void ()> pre, 
    std::function<bool (const MarkVal*)> foreach, 
    std::function<void ()> post)
{
    bool first = true;
    memcpy(working_marking.get(), t_marking, n_places*sizeof(PetriEngine::MarkVal));    
    auto qf = static_cast<QuantifierCondition*>(ptr);
    auto path = ptr->getPath();
    if(!_partial_order || (path != F && path != G) || (*qf)[0]->isTemporal())
    {
        PetriEngine::SuccessorGenerator PNGen(net);
        dowork<PetriEngine::SuccessorGenerator>(PNGen, first, pre, foreach);
    }
    else
    {
        _redgen.setQuery(ptr, path == G);
        dowork<PetriEngine::ReducingSuccessorGenerator>(_redgen, first, pre, foreach);
    }

    if(!first) post();
}

void CTLDependencyGraph::cleanUp()
{    
    while(!recycle.empty())
    {
        assert(recycle.top()->refcnt == -1);
        recycle.pop();
    }
    // TODO, implement proper cleanup
}

size_t CTLDependencyGraph::configurationCount() const
{
    return _configurationCount;
}

size_t CTLDependencyGraph::markingCount() const
{
    return _markingCount;
}

PetriConfig *CTLDependencyGraph::createConfiguration(size_t marking, Condition* t_query)
{
    auto& configs = trie.get_data(marking);
    for(PetriConfig* c : configs){
        if(c->query == t_query)
            return c;
    }

    _configurationCount++;
    size_t id = conf_alloc->next(0);
    char* mem = (*conf_alloc)[id];
    PetriConfig* newConfig = new (mem) PetriConfig();
    newConfig->marking = marking;
    newConfig->query = t_query;
    configs.push_back(newConfig);
    return newConfig;
}



size_t CTLDependencyGraph::createMarking(const MarkVal* t_marking){
    size_t sum = 0;
    bool allsame = true;
    uint32_t val = 0;
    uint32_t active = 0;
    uint32_t last = 0;
    markingStats(t_marking, sum, allsame, val, active, last);
    unsigned char type = encoder.getType(sum, active, allsame, val);
    size_t length = encoder.encode(t_marking, type);
    binarywrapper_t w = binarywrapper_t(encoder.scratchpad().raw(), length*8);
    auto tit = trie.insert(w);
    if(tit.first){
        _markingCount++;
    }

    return tit.second;
}

void CTLDependencyGraph::release(Edge* e)
{
    assert(e->refcnt == 0);
    e->is_negated = false;
    e->processed = false;
    e->source = nullptr;
    e->targets.clear();
    e->refcnt = -1;
    e->handled = false;
    recycle.push(e);
}


Edge* CTLDependencyGraph::newEdge(Configuration &t_source, uint32_t weight)
{
    Edge* e = nullptr;
    if(recycle.empty())
    {
        size_t n = edge_alloc->next(0);
        e = &(*edge_alloc)[n];
    }
    else
    {
        e = recycle.top();
        e->refcnt = 0;
        recycle.pop();
    }
    assert(e->targets.empty());
    e->source = &t_source;
    assert(e->refcnt == 0);
    ++e->refcnt;
    return e;
}

void CTLDependencyGraph::markingStats(const MarkVal* marking, size_t& sum, 
        bool& allsame, uint32_t& val, uint32_t& active, uint32_t& last)
{
    uint32_t cnt = 0;
    for (uint32_t i = 0; i < n_places; i++)
    {
        uint32_t old = val;
        if(marking[i] != 0)
        {
            ++cnt;
            last = std::max(last, i);
            val = std::max(marking[i], val);
            if(old != 0 && marking[i] != old) allsame = false;
            ++active;
            sum += marking[i];
        }
    }
}


}//PetriNet
