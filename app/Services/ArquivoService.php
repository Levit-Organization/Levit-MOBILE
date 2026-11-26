<?php

namespace App\Services;

use App\Exceptions\NaoEncontradoException;
use App\Models\ArquivoModel;
use App\Models\ModuloModel;
use App\Models\RegistroModel;
use CodeIgniter\HTTP\Files\UploadedFile;

class ArquivoService
{
    public const EXTENSOES_PERMITIDAS = [
        'pdf', 'txt', 'csv',
        'doc', 'docx', 'odt',
        'xls', 'xlsx', 'ods',
        'ppt', 'pptx', 'odp',
        'png', 'jpg', 'jpeg', 'gif', 'webp',
    ];

    public const TAMANHO_MAXIMO_BYTES = 10 * 1024 * 1024; // 10 MB

    /**
     * Pelo conteúdo, alguns formatos legítimos são reconhecidos como outro:
     * docx/xlsx/pptx e os do LibreOffice são um zip por dentro, e csv é texto
     * puro. Nesses casos vale a extensão do nome, desde que o conteúdo seja
     * compatível com ela.
     */
    private const CONTEUDO_COMPATIVEL = [
        'zip' => ['docx', 'xlsx', 'pptx', 'odt', 'ods', 'odp'],
        'txt' => ['csv'],
        'doc' => ['xls', 'ppt'], // formatos antigos do Office compartilham o contêiner
    ];

    protected ModuloModel $moduloModel;
    protected RegistroModel $registroModel;
    protected ArquivoModel $arquivoModel;
    protected AutorizacaoModuloService $autorizacaoModuloService;

    public function __construct()
    {
        $this->moduloModel              = new ModuloModel();
        $this->registroModel            = new RegistroModel();
        $this->arquivoModel             = new ArquivoModel();
        $this->autorizacaoModuloService = new AutorizacaoModuloService();
    }

    public function enviarArquivo(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, string $usuarioId, UploadedFile $arquivo): array
    {
        $modulo = $this->moduloModel
            ->where('id', $moduloId)
            ->where('empresa_id', $empresaId)
            ->where('tipo', 'arquivo')
            ->first();

        if (! $modulo) {
            throw new NaoEncontradoException('Tabela de arquivos não encontrada.');
        }

        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        $extensao = $this->validarArquivo($arquivo);
        $nome     = mb_substr($arquivo->getClientName(), 0, 255);
        $chave    = service('storage')->salvar($arquivo->getTempName(), $extensao);

        try {
            $db = db_connect();
            $db->transStart();

            $registroId = $this->registroModel->insert([
                'modulo_id'  => $moduloId,
                'dados'      => [],
                'criado_por' => $usuarioId,
            ]);

            $this->arquivoModel->insert([
                'registro_id'         => $registroId,
                'nome_original'       => $nome,
                'chave_armazenamento' => $chave,
                'tipo_mime'           => mb_substr((string) $arquivo->getMimeType(), 0, 100),
                'tamanho_bytes'       => $arquivo->getSize(),
            ]);

            $db->transComplete();
            $gravou = $db->transStatus() !== false;
        } catch (\Throwable $e) {
            log_message('error', '[ArquivoService] Falha ao gravar o arquivo enviado: {mensagem}', ['mensagem' => $e->getMessage()]);
            $gravou = false;
        }

        if (! $gravou) {
            $this->excluirDoArmazenamento($chave);

            throw new \RuntimeException('Não foi possível concluir o envio. Tente novamente.');
        }

        AtividadeService::registrar('arquivo_enviado', 'Enviou o arquivo “' . $nome . '”', $moduloId);

        return $this->itemDaLista($registroId);
    }

    /**
     * Confere o envio e devolve a extensão com que o arquivo será guardado.
     * O tipo vem do conteúdo, não só do nome: renomear um .exe para .pdf não passa.
     */
    private function validarArquivo(UploadedFile $arquivo): string
    {
        // Acima do upload_max_filesize do PHP o arquivo nem chega: o erro vem do upload.
        if (in_array($arquivo->getError(), [UPLOAD_ERR_INI_SIZE, UPLOAD_ERR_FORM_SIZE], true)) {
            throw new \DomainException('O arquivo passa de 10 MB. Envie um arquivo menor.');
        }

        if ($arquivo->getError() === UPLOAD_ERR_NO_FILE) {
            throw new \DomainException('Nenhum arquivo enviado.');
        }

        if (! $arquivo->isValid()) {
            throw new \DomainException('O arquivo não chegou corretamente. Tente enviar de novo.');
        }

        if ($arquivo->getSize() > self::TAMANHO_MAXIMO_BYTES) {
            throw new \DomainException('O arquivo passa de 10 MB. Envie um arquivo menor.');
        }

        if ($arquivo->getSize() === 0) {
            throw new \DomainException('O arquivo está vazio.');
        }

        $extensao = self::extensaoParaGuardar($arquivo->getClientExtension(), (string) $arquivo->guessExtension());

        if ($extensao === null) {
            throw new \DomainException('Esse tipo de arquivo não é aceito. Envie PDF, imagem, texto, planilha, documento ou apresentação.');
        }

        return $extensao;
    }

    /**
     * Extensão com que o arquivo é guardado, ou null se o tipo não é aceito.
     * `$peloConteudo` é a extensão que o conteúdo indica (guessExtension);
     * o nome só decide quando o conteúdo é compatível com ele (docx é um zip).
     */
    public static function extensaoParaGuardar(string $pelaExtensao, string $peloConteudo): ?string
    {
        $pelaExtensao = strtolower($pelaExtensao);
        $peloConteudo = strtolower($peloConteudo);
        $compativeis  = self::CONTEUDO_COMPATIVEL[$peloConteudo] ?? [];

        if (in_array($pelaExtensao, $compativeis, true)) {
            return $pelaExtensao;
        }

        return in_array($peloConteudo, self::EXTENSOES_PERMITIDAS, true) ? $peloConteudo : null;
    }

    /** O arquivo no mesmo formato da listagem da tabela. */
    private function itemDaLista(string $registroId): array
    {
        return db_connect()->query(
            'SELECT r.id, r.modulo_id, r.criado_por, r.criado_em, r.atualizado_em,
                    a.nome_original AS arquivo_nome, a.tamanho_bytes AS arquivo_tamanho,
                    a.tipo_mime AS arquivo_tipo, u.nome AS enviado_por_nome
               FROM registro r
               JOIN arquivo a ON a.registro_id = r.id
          LEFT JOIN usuario u ON u.id = r.criado_por
              WHERE r.id = ?',
            [$registroId]
        )->getRowArray();
    }

    public function buscarArquivo(string $registroId, string $moduloId, string $empresaId): array
    {
        $arquivo = $this->arquivoModel
            ->select('arquivo.*')
            ->join('registro', 'registro.id = arquivo.registro_id')
            ->join('modulo', 'modulo.id = registro.modulo_id')
            ->where('arquivo.registro_id', $registroId)
            ->where('registro.modulo_id', $moduloId)
            ->where('modulo.empresa_id', $empresaId)
            ->first();

        if (! $arquivo) {
            throw new NaoEncontradoException('Arquivo não encontrado. Ele pode ter sido excluído.');
        }

        return $arquivo;
    }

    public function baixarArquivo(string $registroId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): array
    {
        $arquivo = $this->buscarArquivo($registroId, $moduloId, $empresaId);

        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'visualizar');

        $conteudo = service('storage')->obter($arquivo['chave_armazenamento']);

        return [
            'conteudo'      => $conteudo,
            'nome_original' => $arquivo['nome_original'],
            'tipo_mime'     => $arquivo['tipo_mime'] ?: 'application/octet-stream',
        ];
    }

    /**
     * Sai do banco primeiro: para quem usa, o arquivo some na hora. Se o
     * armazenamento remoto falhar, sobra só um objeto órfão, registrado no log.
     */
    public function excluirArquivo(string $registroId, string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal): void
    {
        $arquivo = $this->buscarArquivo($registroId, $moduloId, $empresaId);

        $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $moduloId, 'editar');

        // O arquivo sai junto pelo ON DELETE CASCADE.
        $this->registroModel->delete($registroId);

        $this->excluirDoArmazenamento($arquivo['chave_armazenamento']);

        AtividadeService::registrar('arquivo_excluido', 'Excluiu o arquivo “' . ($arquivo['nome_original'] ?? '') . '”', $moduloId);
    }

    private function excluirDoArmazenamento(string $chave): void
    {
        try {
            service('storage')->excluir($chave);
        } catch (\Throwable $e) {
            log_message('error', "[ArquivoService] Arquivo remoto não excluído: {$chave} — {$e->getMessage()}");
        }
    }
}
