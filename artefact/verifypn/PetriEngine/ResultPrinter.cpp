
#include "ResultPrinter.h"
#include "PetriNetBuilder.h"
#include "options.h"
#include "PetriEngine/PQL/Expressions.h"

#include <iomanip>


namespace PetriEngine {

    ResultPrinter::ResultPrinter(const PetriNetBuilder& b, const options_t& o, std::vector<std::string>& querynames)
    : builder(b), options(o), querynames(querynames), reducer(NULL) {
    }

    void ResultPrinter::setReducer(Reducer* r) {
        this->reducer = r;
    }

    ResultPrinter::DGResult::DGResult(size_t index, const PQL::Condition* qry) 
    : index(index), query(qry) {
    }

    ResultPrinter::Result ResultPrinter::printResult(
            size_t index,
            const PQL::Condition* query,
            ResultPrinter::Result result,
            size_t exploredStates,
            int maxTokens,
            const std::vector<uint32_t> maxPlaceBound, Structures::StateSetInterface* stateset,
            size_t lastmarking) const {
        if (result == Unknown) return Unknown;

        Result retval = result;

        if (options.cpnOverApprox) {
            if (result == Satisfied)
                retval = query->isInvariant() ? Unknown : Unknown;
            else if (result == NotSatisfied)
                retval = query->isInvariant() ? Satisfied : NotSatisfied;
            if (retval == Unknown) {
                std::cout << "\nUnable to decide if " << querynames[index] << " is satisfied.\n\n";
                std::cout << "Query is MAYBE satisfied.\n" << std::endl;
                return Ignore;
            }
        }
        std::cout << std::endl;

        bool showTrace = (result == Satisfied);

        if (!options.statespaceexploration && retval != Unknown) {
            printQueryName(index);
        } else {
            retval = Satisfied;
            uint32_t placeBound = 0;
            for (size_t p = 0; p < maxPlaceBound.size(); p++) {
                placeBound = std::max<uint32_t>(placeBound, maxPlaceBound[p]);
            }
            // fprintf(stdout,"STATE_SPACE %lli -1 %d %d TECHNIQUES EXPLICIT\n", result.exploredStates(), result.maxTokens(), placeBound);
            std::cout << "STATE_SPACE STATES " << exploredStates << " " << techniquesStateSpace
                    << std::endl
                    << "STATE_SPACE TRANSITIONS " << -1 << " " << techniquesStateSpace
                    << std::endl
                    << "STATE_SPACE MAX_TOKEN_PER_MARKING " << maxTokens << " " << techniquesStateSpace
                    << std::endl
                    << "STATE_SPACE MAX_TOKEN_IN_PLACE " << placeBound << " " << techniquesStateSpace
                    << std::endl;
            return retval;
        }

        if (result == Satisfied)
            retval = query->isInvariant() ? NotSatisfied : Satisfied;
        else if (result == NotSatisfied)
            retval = query->isInvariant() ? Satisfied : NotSatisfied;

        //Print result
        auto bound = query;
        if (auto ef = dynamic_cast<const PQL::EFCondition*> (query)) {
            bound = (*ef)[0].get();
        }
        bound = dynamic_cast<const PQL::UnfoldedUpperBoundsCondition*> (bound);

        if (retval == Unknown) {
            std::cout << "\nUnable to decide if " << querynames[index] << " is satisfied.";
        } else if (bound) {
            std::cout << ((const PQL::UnfoldedUpperBoundsCondition*)bound)->bounds() << " " << techniques << printTechniques() << std::endl;
            printSolvedIndex(index);
        } else if (retval == Satisfied) {
            if (!options.statespaceexploration) {
                printMCCSolved("TRUE", index);
            }
        } else if (retval == NotSatisfied) {
            if (!options.statespaceexploration) {
                printMCCSolved("FALSE", index);
            }
        }

        std::cout << std::endl;

        std::cout << "Query is ";
        if (options.statespaceexploration) {
            retval = Satisfied;
        }

        if (result == Satisfied)
            retval = query->isInvariant() ? NotSatisfied : Satisfied;
        else if (result == NotSatisfied)
            retval = query->isInvariant() ? Satisfied : NotSatisfied;

        //Print result
        if (retval == Unknown) {
            std::cout << "MAYBE ";
        } else if (retval == NotSatisfied) {
            std::cout << "NOT ";
        }
        std::cout << "satisfied." << std::endl;

        if (options.cpnOverApprox)
            std::cout << "\nSolved using CPN Approximation\n" << std::endl;

        if (showTrace && options.trace) {
            if (stateset == NULL) {
#ifdef ENABLE_TAR
                if (options.tar)
                    std::cout << "No trace could be generated" << std::endl;
                else
#endif
                {
                    // No trace was generated, printing the empty trace
                    std::cerr << "Trace:\n<trace>\n";
                    std::cerr << "</trace>\n" << std::endl;
                }
            } else {
                printTrace(stateset, lastmarking);
            }
        }

        std::cout << std::endl;
        return retval;
    }

    std::string ResultPrinter::printTechniques() const {
        std::string out;

        if (options.queryReductionTimeout > 0) {
            out += "LP_APPROX ";
        }

        if (options.cpnOverApprox) {
            out += "CPN_APPROX ";
        }

        if (options.isCPN && !options.cpnOverApprox) {
            out += "UNFOLDING_TO_PT ";
        }

        if (options.queryReductionTimeout == 0
#ifdef ENABLE_TAR
                && !options.tar
#endif
                && options.siphontrapTimeout == 0) {
            out += "EXPLICIT STATE_COMPRESSION ";
            if (options.stubbornreduction) {
                out += "STUBBORN_SETS ";
            }
        }
#ifdef ENABLE_TAR            
        if (options.tar) {
            out += "TRACE_ABSTRACTION_REFINEMENT ";
        }
#endif            
        if (options.siphontrapTimeout > 0) {
            out += "TOPOLOGICAL SIPHON_TRAP ";
        }

        return out;
    }

    void ResultPrinter::printTrace(Structures::StateSetInterface* ss, size_t lastmarking) const {
        std::cerr << "Trace:\n<trace>\n";
        std::stack<size_t> transitions;
        size_t next = lastmarking;
        while (next != 0) // assume 0 is the index of the first marking.
        {
            // (parent, transition)
            std::pair<size_t, size_t> p = ss->getHistory(next);
            next = p.first;
            transitions.push(p.second);
        }

        if (reducer != NULL)
            reducer->initFire(std::cerr);

        while (transitions.size() > 0) {
            size_t trans = transitions.top();
            transitions.pop();
            std::string tname = ss->net().transitionNames()[trans];
            std::cerr << "\t<transition id=\"" << tname << "\">\n";

            // well, yeah, we are not really efficient in constructing the trace.
            // feel free to improve
            for (size_t p = 0; p < ss->net().numberOfPlaces(); ++p) {
                size_t cnt = ss->net().inArc(p, trans);
                for (size_t token = 0; token < cnt; ++token) {
                    std::cerr << "\t\t<token place=\"" << ss->net().placeNames()[p] << "\" age=\"0\"/>\n";
                }
            }

            if (reducer != NULL)
                reducer->extraConsume(std::cerr, tname);

            std::cerr << "\t</transition>\n";

            if (reducer != NULL)
                reducer->postFire(std::cerr, tname);

        }

        std::cerr << "</trace>\n" << std::endl;
    }

    ResultPrinter::Result ResultPrinter::printResult(const DGResult& result) const {

        auto res = printResult(result.index, result.query, result.result);
        
        if (options.printstatistics) {
            cout << "\nSTATS:" << std::endl;
            cout << "	Time (seconds)    : " << std::setprecision(4) << result.duration / 1000 << std::endl;
            cout << "	Configurations    : " << result.numberOfConfigurations << std::endl;
            cout << "	Markings          : " << result.numberOfMarkings << std::endl;
            cout << "	Edges             : " << result.numberOfEdges << std::endl;
            cout << "	Processed Edges   : " << result.processedEdges << std::endl;
            cout << "	Processed N. Edges: " << result.processedNegationEdges << std::endl;
            cout << "	Explored Configs  : " << result.exploredConfigurations << std::endl;
            std::cout << std::endl;
        }
        return res;
    }

    void ResultPrinter::printMCCSolved(const char* result, int index) const
    {
        std::cout << result << " " << techniques << printTechniques() << std::endl;
        printSolvedIndex(index);
    }
    
    void ResultPrinter::printSolvedIndex(int index) const {
        std::cout << "Query index " << index << " was solved" << std::endl;
    }

    void ResultPrinter::printQueryName(int index) const {
        std::cout << "FORMULA " << querynames[index] << " ";
    }
}
