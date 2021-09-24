/* VerifyPN - TAPAAL Petri Net Engine
 * Copyright (C) 2016  Peter Gjøl Jensen <root@petergjoel.dk>
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

#include "Queue.h"
#include "../PQL/Contexts.h"

namespace PetriEngine {
    namespace Structures {
        Queue::Queue() {} 

        bool BFSQueue::pop(size_t& id)
        {
            if(empty()) return false;
            id = _cnt;
            ++_cnt;
            return true;
        }
        
        bool BFSQueue::empty() const {
            return _cnt >= _nstates;
        }
        
        void BFSQueue::push(size_t id, PQL::DistanceContext*,
                PQL::Condition* query)
        {
            ++_nstates;
            // nothing
        }
        
        bool DFSQueue::pop(size_t& id)
        {
            if(empty()) return false;
            id = _stack.top();
            _stack.pop();
            return true;
        }
        
        void DFSQueue::push(size_t id, PQL::DistanceContext*,
                PQL::Condition* query)
        {
            _stack.push(id);
        }

        bool DFSQueue::empty() const {
            return _stack.empty();
        }

        
	size_t genrand(size_t i)
	{
		return std::rand() % i;
	}
        
        bool RDFSQueue::pop(size_t& id)
        {
            if(_cache.empty())
            {
                if(_stack.empty()) return false;
                id = _stack.top();
                _stack.pop();
                return true;                
            }
            else
            {
                std::random_shuffle ( _cache.begin(), _cache.end(), genrand );
		id = _cache.back();
                for(size_t i = 0; i < (_cache.size() - 1); ++i)
                {
                    _stack.push(_cache[i]);
                }
                _cache.clear();
                return true;
            }
        }

        bool RDFSQueue::empty() const {
            return _cache.empty() && _stack.empty();
        }


        void RDFSQueue::push(size_t id, PQL::DistanceContext*,
                PQL::Condition* query)
        {
            _cache.push_back(id);
        }
        
        bool HeuristicQueue::pop(size_t& id)
        {
            if(_queue.empty()) return false;
            id = _queue.top().item;
            _queue.pop();
            return true;
        }
        
        void HeuristicQueue::push(size_t id, PQL::DistanceContext* context,
                PQL::Condition* query)
        {
            // invert result, highest numbers are on top!
            uint32_t dist = query->distance(*context);
            _queue.emplace(dist, (uint32_t)id);
        }

        bool HeuristicQueue::empty() const {
            return _queue.empty();
        }

    }
}
