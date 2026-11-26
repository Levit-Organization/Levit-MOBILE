<?php

namespace App\Models;

class PermissaoModel extends BaseModel
{
    protected $table      = 'permissao';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'codigo',
        'descricao',
    ];

    protected $useTimestamps = false;
}