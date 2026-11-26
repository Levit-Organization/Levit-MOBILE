<?php

namespace App\Models;

class CargoModuloPermissaoModel extends BaseModel
{
    protected $table      = 'cargo_modulo_permissao';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'cargo_id',
        'modulo_id',
        'nivel',
    ];

    protected $useTimestamps = false;
}