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
#ifndef STATESET_H
#define STATESET_H
#include <unordered_map>
#include <iostream>
#include "Utils/Structures/AlignedEncoder.h"
#include "Utils/Structures/ptrie_stable.h"
#include "Utils/Structures/ptrie_map.h"
#include "Utils/Structures/binarywrapper.h"
#include "Utils/errorcodes.h"


namespace PetriEngine {
    namespace Structures {
        
        class StateSetInterface
        {
        public:
            StateSetInterface(const PetriNet& net, uint32_t kbound) :
            _encoder(net.numberOfPlaces(), kbound), _net(net)
            {
                _discovered = 0;
                _kbound = kbound;
                _maxTokens = 0;
                _maxPlaceBound = std::vector<uint32_t>(_net.numberOfPlaces(), 0);
                _sp = binarywrapper_t(sizeof(uint32_t)*_net.numberOfPlaces()*8);
            }

	    ~StateSetInterface()
	    {
		_sp.release();
	    }
            
            virtual std::pair<bool, size_t> add(const MarkVal* state) = 0;
            
            virtual void decode(MarkVal* state, size_t id) = 0;
            
            const PetriNet& net() { return _net;}
            
            virtual void setHistory(size_t id, size_t transition) = 0;
            
            virtual std::pair<size_t, size_t> getHistory(size_t markingid) = 0;
            
            virtual size_t size() = 0;
            
        protected:
            size_t _discovered;
            uint32_t _kbound;
            uint32_t _maxTokens;
            std::vector<uint32_t> _maxPlaceBound;
            AlignedEncoder _encoder;
            const PetriNet& _net;
            binarywrapper_t _sp;     
#ifdef DEBUG
            std::vector<uint32_t*> _dbg;
#endif
            template<typename T>
            void _decode(MarkVal* state, size_t id, T& _trie)
            {
                    _trie.unpack(id, _encoder.scratchpad().raw());
                    _encoder.decode(state, _encoder.scratchpad().raw());

#ifdef DEBUG
                    assert(memcmp(state, _dbg[id], sizeof(uint32_t)*_net.numberOfPlaces()) == 0);
#endif
            }             
            
            template<typename T>
            std::pair<bool, size_t> _add(const MarkVal* state, T& _trie) {
                _discovered++;
                
#ifdef DEBUG
                if(_discovered % 1000000 == 0) std::cout << "Found number " << _discovered << std::endl;
#endif
                
                MarkVal sum = 0;
                bool allsame = true;
                uint32_t val = 0;
                uint32_t active = 0;
                uint32_t last = 0;
                markingStats(state, sum, allsame, val, active, last);               

                if (_maxTokens < sum)
                    _maxTokens = sum;

                //Check that we're within k-bound
                if (_kbound != 0 && sum > _kbound)
                    return std::pair<bool, size_t>(false, std::numeric_limits<size_t>::max());
                    
                unsigned char type = _encoder.getType(sum, active, allsame, val);


                size_t length = _encoder.encode(state, type);
                binarywrapper_t w = binarywrapper_t(_encoder.scratchpad().raw(), length*8);
                auto tit = _trie.insert(w);
            
                
                if(!tit.first)
                {
                    return tit;
                }
                
#ifdef DEBUG
                _dbg.push_back(new uint32_t[_net.numberOfPlaces()]);
                memcpy(_dbg.back(), state, _net.numberOfPlaces()*sizeof(uint32_t));
                decode(state, _trie.size() - 1);
#endif
           
                // update the max token bound for each place in the net (only for newly discovered markings)
                for (uint32_t i = 0; i < _net.numberOfPlaces(); i++) 
                {
                    _maxPlaceBound[i] = std::max<MarkVal>( state[i],
                                                            _maxPlaceBound[i]);
                }

#ifdef DEBUG    
                if(_trie.size() % 100000 == 0) std::cout << "Inserted " << _trie.size() << std::endl;
#endif     
                return tit;
            }

        public:
            size_t discovered() const {
                return _discovered;
            }

            uint32_t maxTokens() const {
                return _maxTokens;
            }
            
            std::vector<MarkVal> maxPlaceBound() const {
                return _maxPlaceBound;
            }
            
        protected:
            
            void markingStats(const uint32_t* marking, MarkVal& sum, bool& allsame, uint32_t& val, uint32_t& active, uint32_t& last)
            {
                uint32_t cnt = 0;
                
                for (uint32_t i = 0; i < _net.numberOfPlaces(); i++)
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
        };
        
#define STATESET_BUCKETS 1000000
        class StateSet : public StateSetInterface {
        private:
            using wrapper_t = ptrie::binarywrapper_t;
            using ptrie_t = ptrie::set_stable<>;
            
        public:
            using StateSetInterface::StateSetInterface;
             
            virtual std::pair<bool, size_t> add(const MarkVal* state) override
            {
                return _add(state, _trie);
            }
            
            virtual void decode(MarkVal* state, size_t id) override
            {
                _decode(state, id, _trie);
            }
            
            
            virtual void setHistory(size_t id, size_t transition) override {}

            virtual std::pair<size_t, size_t> getHistory(size_t markingid) override
            { 
                assert(false); 
                return std::make_pair(0,0); 
            }
            
            size_t size() override
            {
                return _trie.size();
            }

            
        private:               
            ptrie_t _trie;
        };

        template<typename T>
        class AnnotatedStateSet : public StateSetInterface {
        private:
            using wrapper_t = ptrie::binarywrapper_t;
            using ptrie_t = ptrie::map<T>;
            
        public:
            using StateSetInterface::StateSetInterface;
             
            virtual std::pair<bool, size_t> add(const MarkVal* state) override
            {
                return _add(state, _trie);
            }
            
            virtual void decode(MarkVal* state, size_t id) override
            {
                _decode(state, id, _trie);
            }
            
            T& get_data(size_t markingid){
                return _trie.get_data(markingid);
            }

            virtual void setHistory(size_t id, size_t transition) override {}

            virtual std::pair<size_t, size_t> getHistory(size_t markingid) override
            { 
                assert(false); 
                return std::make_pair(0,0); 
            }
            
            size_t size() override
            {
                return _trie.size();
            }

            
        protected:               
            ptrie_t _trie;
        };  

        struct traceable_t
        {
            ptrie::uint parent;
            ptrie::uint transition;
        };
        
        class TracableStateSet : public AnnotatedStateSet<traceable_t>
        {            
            
        public:
            using AnnotatedStateSet<traceable_t>::AnnotatedStateSet;

            virtual void decode(MarkVal* state, size_t id) override
            {
                _parent = id;
                AnnotatedStateSet<traceable_t>::decode(state, id);
            }
                       
            virtual void setHistory(size_t id, size_t transition) override 
            {
                traceable_t& t = get_data(id);
                t.parent = _parent;
                t.transition = transition;
            }
            
            virtual std::pair<size_t, size_t> getHistory(size_t markingid) override
            {
                traceable_t& t = get_data(markingid);
                return std::pair<size_t, size_t>(t.parent, t.transition);
            }
            
            size_t size() override
            {
                return _trie.size();
            }

            
        private:
            size_t _parent = 0;
        };
        
    }
}


#endif // STATESET_H
