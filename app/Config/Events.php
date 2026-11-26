<?php

namespace Config;

use App\Services\AtividadeService;
use App\Services\Automacao\ModulosComAutomacao;
use CodeIgniter\Events\Events;
use CodeIgniter\Exceptions\FrameworkException;
use CodeIgniter\HotReloader\HotReloader;

/*
 * --------------------------------------------------------------------
 * Application Events
 * --------------------------------------------------------------------
 * Events allow you to tap into the execution of the program without
 * modifying or extending core files. This file provides a central
 * location to define your events, though they can always be added
 * at run-time, also, if needed.
 *
 * You create code that can execute by subscribing to events with
 * the 'on()' method. This accepts any form of callable, including
 * Closures, that will be executed when the event is triggered.
 *
 * Example:
 *      Events::on('create', [$myInstance, 'myMethod']);
 */

Events::on('pre_system', static function (): void {
    if (ENVIRONMENT !== 'testing') {
        $value = ini_get('zlib.output_compression');

        if (filter_var($value, FILTER_VALIDATE_BOOLEAN) || (int) $value > 0) {
            throw FrameworkException::forEnabledZlibOutputCompression();
        }

        while (ob_get_level() > 0) {
            ob_end_flush();
        }

        ob_start(static fn ($buffer) => $buffer);
    }

    /*
     * --------------------------------------------------------------------
     * Debug Toolbar Listeners.
     * --------------------------------------------------------------------
     * If you delete, they will no longer be collected.
     */
    if (CI_DEBUG && ! is_cli()) {
        Events::on('DBQuery', 'CodeIgniter\Debug\Toolbar\Collectors\Database::collect');
        service('toolbar')->respond();
        // Hot Reload route - for framework use on the hot reloader.
        if (ENVIRONMENT === 'development') {
            service('routes')->get('__hot-reload', static function (): void {
                (new HotReloader())->run();
            });
        }
    }
});

// Só enfileira quando a tabela tem automação ligada (ver ModulosComAutomacao).
Events::on('registro_criado', static function (array $payload) {
    if (! ModulosComAutomacao::temAtiva($payload['modulo_id'])) {
        return;
    }
    service('queue')->push('automacoes', 'processar-automacoes', array_merge($payload, ['gatilho' => 'criacao']));
});

Events::on('registro_atualizado', static function (array $payload) {
    if (! ModulosComAutomacao::temAtiva($payload['modulo_id'])) {
        return;
    }
    service('queue')->push('automacoes', 'processar-automacoes', array_merge($payload, ['gatilho' => 'atualizacao']));
});

Events::on('registro_excluido', static function (array $payload) {
    if (! ModulosComAutomacao::temAtiva($payload['modulo_id'])) {
        return;
    }
    service('queue')->push('automacoes', 'processar-automacoes', array_merge($payload, ['gatilho' => 'exclusao']));
});

Events::on('candidato_criado', static function (array $payload) {
    if (! ModulosComAutomacao::temAtiva($payload['modulo_id'])) {
        return;
    }
    service('queue')->push('automacoes', 'processar-automacoes', array_merge($payload, ['gatilho' => 'candidato_criado']));
});

Events::on('candidato_mudou_etapa', static function (array $payload) {
    if (! ModulosComAutomacao::temAtiva($payload['modulo_id'])) {
        return;
    }
    service('queue')->push('automacoes', 'processar-automacoes', array_merge($payload, ['gatilho' => 'candidato_mudou_etapa']));
});

// Histórico de atividades (base dos relatórios). Gravado depois da resposta.
Events::on('registro_criado', static fn (array $p) => AtividadeService::registrarItem('item_criado', $p));
Events::on('registro_atualizado', static fn (array $p) => AtividadeService::registrarItem('item_alterado', $p));
Events::on('registro_excluido', static fn (array $p) => AtividadeService::registrarItem('item_excluido', $p));
Events::on('candidato_criado', static fn (array $p) => AtividadeService::registrarCandidato('candidatura_recebida', $p));
Events::on('candidato_mudou_etapa', static fn (array $p) => AtividadeService::registrarCandidato('candidato_movido', $p));
