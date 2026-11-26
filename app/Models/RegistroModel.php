<?php

namespace App\Models;

class RegistroModel extends BaseModel
{
    protected $table      = 'registro';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'modulo_id',
        'dados',
        'criado_por',
        'atualizado_por',
        'recorrencia_origem_id',
    ];

    protected array $casts = [
        'dados' => '?json-array',
    ];

    protected $useTimestamps = true;
    protected $createdField  = 'criado_em';
    protected $updatedField  = 'atualizado_em';
    protected $dateFormat    = 'datetime';
}