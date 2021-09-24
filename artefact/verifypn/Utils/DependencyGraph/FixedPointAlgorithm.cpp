#include "FixedPointAlgorithm.h"
#include "CertainZeroFPA.h"
#include "LocalFPA.h"
#include "SearchStrategy/BFSSearch.h"
#include "SearchStrategy/DFSSearch.h"
#include "SearchStrategy/RDFSSearch.h"
#include "SearchStrategy/HeuristicSearch.h"
#include "../errorcodes.h"

#include <iostream>
#include <memory>

namespace DependencyGraph {

    FixedPointAlgorithm::FixedPointAlgorithm(Utils::SearchStrategies::Strategy type) {
        using namespace Utils::SearchStrategies;
        using namespace SearchStrategy;
        switch (type) {
            case DFS:
                strategy = std::make_shared<DFSSearch>();
                break;
            case RDFS:
                strategy = std::make_shared<RDFSSearch>();
                break;
            case BFS:
                strategy = std::make_shared<BFSSearch>();
                break;
            case HEUR:
                strategy = std::make_shared<HeuristicSearch>();
                break;
            default:
                std::cerr << "Search strategy is unsupported by the CTL-Engine" << std::endl;
                assert(false);
                std::exit(ErrorCode);
        }
    }

    ReturnValue FixedPointAlgorithm::getAlgorithm(std::shared_ptr<DependencyGraph::FixedPointAlgorithm>& algorithm, DependencyGraph::AlgorithmType algorithmtype, Utils::SearchStrategies::Strategy search) 
    {
        {
            switch (algorithmtype) {
                case DependencyGraph::Local:
                    algorithm = std::make_shared<DependencyGraph::LocalFPA>(search);
                    break;
                case DependencyGraph::CZero:
                    algorithm = std::make_shared<DependencyGraph::CertainZeroFPA>(search);
                    break;
                default:
                    std::cerr << "Error: Unknown or unsupported algorithm" << std::endl;
                    return ErrorCode;
            }
            return ContinueCode;
        }
    }

}