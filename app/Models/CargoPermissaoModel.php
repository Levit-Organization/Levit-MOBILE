<?php

namespace App\Models;

class CargoPermissaoModel extends BaseModel
{
    protected $table      = 'cargo_permissao';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'cargo_id',
        'permissao_id',
    ];

    protected $useTimestamps = false;
}