<?php

namespace App\Models;

class EmpresaModel extends BaseModel
{
    protected $table      = 'empresa';
    protected $primaryKey = 'id';

    protected $returnType = 'array';

    protected $allowedFields = [
        'nome',
        'cnpj_cpf',
        'setor_atuacao',
        'email_corporativo',
        'logo',
        'administrador_principal_id',
    ];

    protected $useTimestamps = true;
    protected $createdField  = 'criado_em';
    protected $updatedField  = 'atualizado_em';
    protected $dateFormat    = 'datetime';
}

?>