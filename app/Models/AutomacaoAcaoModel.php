<?php

namespace App\Models;

class AutomacaoAcaoModel extends BaseModel
{
    protected $table      = 'automacao_acao';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'automacao_id',
        'tipo',
        'configuracao',
        'ordem',
    ];

    protected array $casts = [
        'configuracao' => '?json-array',
    ];

    protected $useTimestamps = false;
}