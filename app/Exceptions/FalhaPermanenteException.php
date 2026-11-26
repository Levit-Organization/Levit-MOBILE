<?php

namespace App\Exceptions;

/**
 * Falha que não adianta repetir (configuração errada, destinatário inválido,
 * webhook respondendo 4xx). Automações não fazem retry nesses casos.
 */
class FalhaPermanenteException extends \RuntimeException
{
}
