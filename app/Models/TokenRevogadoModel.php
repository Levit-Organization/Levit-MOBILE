<?php

namespace App\Models;

class TokenRevogadoModel extends BaseModel
{
    protected $table      = 'token_revogado';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'jti',
        'usuario_id',
        'expira_em',
    ];

    protected $useTimestamps = false;
}