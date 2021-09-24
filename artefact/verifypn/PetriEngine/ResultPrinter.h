/* PeTe - Petri Engine exTremE
 * Copyright (C) 2011  Jonas Finnemann Jensen <jopsen@gmail.com>,
 *                     Thomas Søndersø Nielsen <primogens@gmail.com>,
 *                     Lars Kærlund Østergaard <larsko@gmail.com>,
 *                     Peter Gjøl Jensen <root@petergjoel.dk>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
#ifndef REACHABILITYRESULT_H
#define REACHABILITYRESULT_H

#include <vector>
#include "PQL/PQL.h"
#include "Structures/StateSet.h"
#include "Reducer.h"

struct options_t;

namespace PetriEngine {
    class PetriNetBuilder;

    /** Result of a reachability search */

    class ResultPrinter {
    protected:
        const PetriNetBuilder& builder;
        const options_t& options;
        std::vector<std::string>& querynames;
        Reducer* reducer;
    public:

        /** Types of results */
        enum Result {
            /** The query was satisfied */
            Satisfied,
            /** The query cannot be satisfied */
            NotSatisfied,
            /** We're unable to say if the query can be satisfied */
            Unknown,
            /** The query should be verified using the CTL engine */
            CTL,
            /** Just ignore */
            Ignore
        };

        struct DGResult {
            DGResult(size_t index, const PQL::Condition* qry);
            size_t index;
            const PQL::Condition* query = nullptr;
            ResultPrinter::Result result = Unknown;

            double duration = 0;
            size_t numberOfMarkings = 0;
            size_t numberOfConfigurations = 0;
            size_t processedEdges = 0;
            size_t processedNegationEdges = 0;
            size_t exploredConfigurations = 0;
            size_t numberOfEdges = 0;
        };
        
        const char* techniques = "TECHNIQUES COLLATERAL_PROCESSING STRUCTURAL_REDUCTION QUERY_REDUCTION SAT_SMT ";
        const char* techniquesStateSpace = "TECHNIQUES EXPLICIT STATE_COMPRESSION";

        ResultPrinter(const PetriNetBuilder& b, const options_t& o, std::vector<std::string>& querynames);

        void setReducer(Reducer* r);

        Result printResult(
                size_t index,
                const PQL::Condition* query,
                ResultPrinter::Result result = Unknown,
                size_t expandedStates = 0,
                int maxTokens = 0,
                const std::vector<uint32_t> maxPlaceBound = std::vector<uint32_t>(),
                Structures::StateSetInterface* stateset = nullptr, size_t lastmarking = 0) const;
        
        Result printResult(const DGResult&) const;

        std::string printTechniques() const;

        void printTrace(Structures::StateSetInterface*, size_t lastmarking) const;
        void printMCCSolved(const char* result, int index) const;
        void printSolvedIndex(int index) const;
        void printQueryName(int index) const;
    };
} // PetriEngine

#endif // REACHABILITYRESULT_H
