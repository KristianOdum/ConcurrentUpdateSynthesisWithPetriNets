#ifndef CTLENGINE_H
#define CTLENGINE_H

#include "PetriEngine/ResultPrinter.h"
#include "PetriEngine/PetriNet.h"
#include "PetriEngine/PQL/PQL.h"
#include "PetriEngine/options.h"

#include "Utils/DependencyGraph/AlgorithmTypes.h"
#include "Utils/SearhStrategies.h"
#include "Utils/errorcodes.h"

ReturnValue CTLMain(PetriEngine::PetriNet& net,
                    DependencyGraph::AlgorithmType algorithmtype,
                    Utils::SearchStrategies::Strategy strategytype,
                    bool partial_order,
                    const std::vector<std::shared_ptr<PetriEngine::PQL::Condition>>& reducedQueries,
                    const std::vector<size_t>& ids,
                    PetriEngine::ResultPrinter& printer);

#endif // CTLENGINE_H
