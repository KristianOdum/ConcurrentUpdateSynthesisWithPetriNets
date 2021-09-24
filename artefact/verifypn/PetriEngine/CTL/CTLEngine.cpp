#include "CTLEngine.h"

#include "CTLDependencyGraph.h"
#include "../ResultPrinter.h"

#include "Utils/DependencyGraph/Edge.h"
#include "Utils/DependencyGraph/SearchStrategy/DFSSearch.h"

#include "Utils/DependencyGraph/CertainZeroFPA.h"
#include "Utils/DependencyGraph/LocalFPA.h"

#include "PetriEngine/PQL/PQL.h"
#include "PetriEngine/options.h"

#include "Utils/Stopwatch.h"
#include "Utils/DependencyGraph/AlgorithmTypes.h"

#include <iostream>
#include <iomanip>
#include <fstream>
#include <sstream>
#include <vector>

using namespace PetriEngine;
using namespace PetriEngine::PQL;

ReturnValue CTLMain(PetriEngine::PetriNet& net,
        DependencyGraph::AlgorithmType algorithmtype,
        Utils::SearchStrategies::Strategy strategytype,
        bool partial_order,
        const std::vector<std::shared_ptr<Condition>>&queries,
        const std::vector<size_t>& querynumbers,
        PetriEngine::ResultPrinter& printer
        ) {

    for (auto qnum : querynumbers) {
        ResultPrinter::DGResult result(qnum, queries[qnum].get());
        PetriEngine::CTLDependencyGraph graph(net, queries[qnum], partial_order);
        std::shared_ptr<DependencyGraph::FixedPointAlgorithm> alg = nullptr;
        bool solved = false;
        switch (graph.initialEval()) {
            case Result::RFALSE:
                result.result = ResultPrinter::NotSatisfied;
                solved = true;
                break;
            case Result::RTRUE:
                result.result = ResultPrinter::Satisfied;
                solved = true;
                break;
            default:
                break;
        }

        if (!solved) {
            if (DependencyGraph::FixedPointAlgorithm::getAlgorithm(alg, algorithmtype, strategytype) == ErrorCode) {
                return ErrorCode;
            }

            stopwatch timer;
            timer.start();
            result.result = alg->search(graph) ? ResultPrinter::Satisfied : ResultPrinter::NotSatisfied;
            timer.stop();
            result.duration = timer.duration();
        }
        result.numberOfConfigurations = graph.configurationCount();
        result.numberOfMarkings = graph.markingCount();
        result.processedEdges = alg ? alg->processedEdges() : 0;
        result.processedNegationEdges = alg ? alg->processedNegationEdges() : 0;
        result.exploredConfigurations = alg ? alg->exploredConfigurations() : 0;
        result.numberOfEdges = alg ? alg->numberOfEdges() : 0;
        printer.printResult(result);
    }
    return SuccessCode;
}
