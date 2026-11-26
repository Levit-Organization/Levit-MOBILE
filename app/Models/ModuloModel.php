<?php

namespace App\Models;

class ModuloModel extends BaseModel
{
    protected $table      = 'modulo';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'empresa_id',
        'nome',
        'icone',
        'tipo',
        'criado_por',
    ];

    protected $useTimestamps = true;
    protected $createdField  = 'criado_em';
    protected $updatedField  = 'atualizado_em';
    protected $dateFormat    = 'datetime';
}