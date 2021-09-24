/* PeTe - Petri Engine exTremE
 * Copyright (C) 2011  Jonas Finnemann Jensen <jopsen@gmail.com>,
 *                     Thomas Søndersø Nielsen <primogens@gmail.com>,
 *                     Lars Kærlund Østergaard <larsko@gmail.com>,
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
#include "PQL.h"
#include "Contexts.h"
#include "Expressions.h"

namespace PetriEngine {
    namespace PQL {

        Expr::~Expr(){
        }
        
        bool Condition::isTriviallyTrue() {
            if (trivial == 1) {
                return true;
            }
            
            return false;
        }
        
        bool Condition::isTriviallyFalse() {
            if (trivial == 2) {
                return true;
            }
            
            return false;
        }
        
        Condition::~Condition() {

        }

        Condition_ptr Condition::initialMarkingRW(std::function<Condition_ptr()> func, negstat_t& stats, const EvaluationContext& context, bool nested, bool negated, bool initrw)
        {
            auto res = func();
            if(!nested && initrw)
            {
                auto e = res->evaluate(context).first;
                if(e != RUNKNOWN) 
                {
                    if(res->getQuantifier() == E && res->getPath() == F)
                    {
                        auto ef = static_cast<EFCondition*>(res.get());
                        if(dynamic_cast<UnfoldedUpperBoundsCondition*>((*ef)[0].get()))
                        {
                            return res;
                        }
                    }
                    return BooleanCondition::getShared(e);
                }
            }
            return res;            
        }

        bounds_t bounds_t::operator*(const bounds_t& other) const
        {
            return bounds_t(value*other.value, lower*other.lower, upper*other.upper);
        }
        
        bounds_t bounds_t::operator +(const bounds_t& other) const {
            return bounds_t(value+other.value, lower+other.lower, upper+other.upper);
        }
        
        bounds_t bounds_t::operator -(const bounds_t& other) const {
            return bounds_t(value-other.value, lower-other.lower, upper-other.upper);
        }

        StableResult bounds_t::operator==(const bounds_t& other) const
        {
            auto rr = value == other.value;
            if(rr)
                return std::make_pair(RTRUE, other.lower == other.upper && lower == upper);
            else
                return std::make_pair(RFALSE, other.upper < lower || upper < other.lower);
        }
        
        StableResult bounds_t::operator!=(const bounds_t& other) const
        {
            auto res = *this == other;
            res.first = res.first != RTRUE ? RTRUE : RFALSE;
            return res;
        }

        StableResult bounds_t::operator<(const bounds_t& other) const
        {
            auto rr = value < other.value;
            if(rr)
                return std::make_pair(rr ? RTRUE : RFALSE, upper < other.lower);
            else
                return std::make_pair(rr ? RTRUE : RFALSE, lower >= other.upper);
        }

        StableResult bounds_t::operator<=(const bounds_t& other) const
        {
            auto res = other < *this;
            res.first = res.first != RTRUE  ? RTRUE : RFALSE;
            return res;
        }

        StableResult bounds_t::operator>(const bounds_t& other) const
        {
            return other < *this;
        }

        StableResult bounds_t::operator>=(const bounds_t& other) const
        {
            return other <= *this;
        }
        
    } // PQL
} // PetriEngine
