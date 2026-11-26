<?php

namespace App\Models;

class AutomacaoLogModel extends BaseModel
{
    protected $table      = 'automacao_log';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'automacao_id',
        'registro_id',
        'status',
        'detalhes',
        'gatilho',
        'tentativa',
        'duracao_ms',
        'passos',
        'dados',
    ];

    protected array $casts = [
        'tentativa'  => 'int',
        'duracao_ms' => '?int',
        'passos'     => '?json-array',
        'dados'      => '?json-array',
    ];

    protected $useTimestamps = false;
}
