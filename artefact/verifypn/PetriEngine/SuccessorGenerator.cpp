/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/* 
 * File:   SuccessorGenerator.cpp
 * Author: Peter G. Jensen
 * 
 * Created on 30 March 2016, 19:50
 */

#include "SuccessorGenerator.h"
#include "Utils/errorcodes.h"

#include <cassert>
#include <cstring>
namespace PetriEngine {

    SuccessorGenerator::SuccessorGenerator(const PetriNet& net, bool is_game, bool is_safety)
    : _net(net), _parent(nullptr), _is_game(is_game), _is_safety(is_safety) {
        reset();
    }
    
    SuccessorGenerator::SuccessorGenerator(const PetriNet& net, std::vector<std::shared_ptr<PQL::Condition> >& queries, bool is_game, bool is_safety) 
    : SuccessorGenerator(net, is_game, is_safety){}

    SuccessorGenerator::~SuccessorGenerator() {
    }

    void SuccessorGenerator::prepare(const MarkVal* state) {
        _parent = state;
        reset();
    }

    void SuccessorGenerator::reset() {
        _suc_pcounter = 0;
        _suc_tcounter = std::numeric_limits<uint32_t>::max();
    }

    void SuccessorGenerator::consumePreset(MarkVal* write, uint32_t t) {

        const TransPtr& ptr = _net._transitions[t];
        uint32_t finv = ptr.inputs;
        uint32_t linv = ptr.outputs;
        for (; finv < linv; ++finv) {
            if(!_net._invariants[finv].inhibitor) {
                assert(write[_net._invariants[finv].place] >= _net._invariants[finv].tokens);
                write[_net._invariants[finv].place] -= _net._invariants[finv].tokens;
            }
        }
    }

    bool SuccessorGenerator::checkPreset(uint32_t t) {
        const TransPtr& ptr = _net._transitions[t];
        uint32_t finv = ptr.inputs;
        uint32_t linv = ptr.outputs;

        for (; finv < linv; ++finv) {
            const Invariant& inv = _net._invariants[finv];
            if (_parent[inv.place] < inv.tokens) {
                if (!inv.inhibitor) {
                    return false;
                }
            } else {
                if (inv.inhibitor) {
                    return false;
                }
            }
        }
        return true;
    }

    void SuccessorGenerator::producePostset(MarkVal* write, uint32_t t) {
        const TransPtr& ptr = _net._transitions[t];
        uint32_t finv = ptr.outputs;
        uint32_t linv = _net._transitions[t + 1].inputs;

        for (; finv < linv; ++finv) {
            size_t n = write[_net._invariants[finv].place];
            n += _net._invariants[finv].tokens;
            if (n >= std::numeric_limits<uint32_t>::max()) {
                std::cerr << "Exceeded 2**32 limit of tokens in a single place ("  << n << ")" << std::endl;
                std::exit(FailedCode);
            }
            write[_net._invariants[finv].place] = n;
        }
    }

    bool SuccessorGenerator::next(MarkVal* write, PetriNet::player_t player) {
        for (; _suc_pcounter < _net._nplaces; ++_suc_pcounter) {
            // orphans are currently under "place 0" as a special case
            if (_suc_pcounter == 0 || _parent[_suc_pcounter] > 0) {
                if (_suc_tcounter == std::numeric_limits<uint32_t>::max()) {
                    _suc_tcounter = _net._placeToPtrs[_suc_pcounter];
                }
                uint32_t last = _net._placeToPtrs[_suc_pcounter + 1];
                for (; _suc_tcounter != last; ++_suc_tcounter) {
                    if (_is_game && !_net.ownedBy(_suc_tcounter, player))
                        continue;
                    if (!checkPreset(_suc_tcounter)) continue;
                    memcpy(write, _parent, _net._nplaces * sizeof (MarkVal));
                    consumePreset(write, _suc_tcounter);
                    producePostset(write, _suc_tcounter);
                    //std::cerr << "SuccessorGenerator next " << std::endl;
                    //_net.print(write);
                    ++_suc_tcounter;
                    return true;
                }
                _suc_tcounter = std::numeric_limits<uint32_t>::max();
            }
            _suc_tcounter = std::numeric_limits<uint32_t>::max();
        }
        reset();
        return false;
    }
}

