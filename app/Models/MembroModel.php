<?php

namespace App\Models;

/** Vínculo de uma pessoa com uma equipe (empresa), com o cargo dela ali. */
class MembroModel extends BaseModel
{
    protected $table      = 'membro';
    protected $primaryKey = 'id';
    protected $returnType = 'array';

    protected $allowedFields = [
        'usuario_id',
        'empresa_id',
        'cargo_id',
    ];

    protected $useTimestamps = false;
}
