<?php

namespace App\Services\Recrutamento;

use CodeIgniter\HTTP\Files\UploadedFile;

/**
 * Currículo do candidato: o que é aceito no envio e a guarda no storage.
 * No banco ficam só a chave, o nome original, o tipo e o tamanho.
 */
final class Curriculo
{
    private const EXTENSOES     = ['pdf', 'doc', 'docx'];
    private const MAXIMO_BYTES  = 5 * 1024 * 1024; // 5 MB

    /** Veio um arquivo (o campo pode chegar vazio quando nada foi escolhido). */
    public static function foiEnviado(?UploadedFile $curriculo): bool
    {
        return $curriculo !== null && $curriculo->getError() !== UPLOAD_ERR_NO_FILE;
    }

    /**
     * Confere e sobe o arquivo; devolve as colunas do candidato.
     *
     * @return array{curriculo_chave: string, curriculo_nome: string, curriculo_mime: string, curriculo_tamanho: int}
     *
     * @throws \DomainException com a mensagem para quem enviou
     */
    public static function salvar(UploadedFile $curriculo): array
    {
        // Acima do upload_max_filesize do PHP o arquivo nem chega: o erro vem do upload.
        if (in_array($curriculo->getError(), [UPLOAD_ERR_INI_SIZE, UPLOAD_ERR_FORM_SIZE], true)) {
            throw new \DomainException('O currículo passa do tamanho permitido. Envie um arquivo menor.');
        }

        if (! $curriculo->isValid()) {
            throw new \DomainException('O currículo não chegou corretamente. Tente enviar de novo.');
        }

        $extensao = strtolower($curriculo->guessExtension() ?: $curriculo->getClientExtension());

        if (! in_array($extensao, self::EXTENSOES, true)) {
            throw new \DomainException('Envie o currículo em PDF ou Word (.doc ou .docx).');
        }

        if ($curriculo->getSize() > self::MAXIMO_BYTES) {
            throw new \DomainException('O currículo passa de 5 MB. Envie um arquivo menor.');
        }

        return [
            'curriculo_chave'   => service('storage')->salvar($curriculo->getTempName(), $extensao),
            'curriculo_nome'    => mb_substr($curriculo->getClientName(), 0, 255),
            'curriculo_mime'    => $curriculo->getMimeType(),
            'curriculo_tamanho' => $curriculo->getSize(),
        ];
    }

    public static function obter(string $chave): string
    {
        return service('storage')->obter($chave);
    }

    public static function excluir(string $chave): void
    {
        service('storage')->excluir($chave);
    }
}
