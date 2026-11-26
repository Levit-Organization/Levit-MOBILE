<?php

namespace App\Models;

class CampoModuloModel extends BaseModel
{
    protected $table      = 'campo_modulo';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'modulo_id',
        'nome',
        'tipo',
        'opcoes',
        'ordem',
        'modulo_vinculado_id',
    ];

    protected array $casts = [
        'opcoes' => '?json-array',
    ];

    protected $useTimestamps = true;
    protected $createdField  = 'criado_em';
    protected $updatedField  = 'atualizado_em';
    protected $dateFormat    = 'datetime';
}