<?php

namespace App\Models;

class AutomacaoModel extends BaseModel
{
    protected $table      = 'automacao';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'modulo_id',
        'nome',
        'gatilho',
        'gatilhos',
        'campo_condicao_id',
        'condicao_operador',
        'condicao_valor',
        'condicao_valor_de',
        'condicoes',
        'ativo',
        'criado_por',
        'pausada_por_falhas_em',
    ];

    protected array $casts = [
        'gatilhos'  => '?json-array',
        'condicoes' => '?json-array',
    ];

    protected $useTimestamps = true;
    protected $createdField  = 'criado_em';
    protected $updatedField  = 'atualizado_em';
    protected $dateFormat    = 'datetime';
}