<?php

namespace App\Models;

class UsuarioModel extends BaseModel
{
    protected $table      = 'usuario';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'ultima_empresa_id',
        'nome',
        'email',
        'senha_hash',
        'reset_hash',
        'reset_expires_at',
        'senha_alterada_em',
    ];

    protected $useTimestamps = true;
    protected $createdField  = 'criado_em';
    protected $updatedField  = 'atualizado_em';
    protected $dateFormat    = 'datetime';
}

?>