<?php

namespace App\Models;

class CategoriaFinanceiraModel extends BaseModel
{
    protected $table      = 'categoria_financeira';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'empresa_id',
        'nome',
    ];

    protected $useTimestamps = false;
}