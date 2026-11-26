<?php

namespace App\Services\Auth;

/**
 * O que o AuthFilter consulta em toda requisição autenticada (ver
 * AuthService::contextoDeAutenticacao()), mais a conferência da coluna
 * `usuario.senha_alterada_em` e a leitura de BOOLEAN vindo do banco.
 */
class ContextoDeAutenticacao
{
    /**
     * Tudo o que o filtro de autenticação precisa, numa única ida ao banco:
     * se o token foi revogado, o cargo, a flag de acesso total, as permissões
     * do cargo, o nível do cargo em cada tabela e quando a senha mudou pela
     * última vez. Com banco remoto, cada consulta a menos é uma volta de rede
     * a menos em toda requisição (os níveis evitam mais uma consulta em toda
     * rota de tabela).
     *
     * Retorna `null` se o usuário não existir mais ou não fizer mais parte
     * da equipe do token (foi removido dela).
     *
     * @return array{revogado: bool, cargo_id: string, acesso_total: bool, permissoes: string[], niveis: array<string, string>, senha_alterada_em: ?string}|null
     */
    public function buscar(string $usuarioId, string $empresaId, string $jti): ?array
    {
        // Token sem equipe válida não chega ao banco (o cast para UUID quebraria a consulta).
        $uuid = '/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i';
        if (! preg_match($uuid, $usuarioId) || ! preg_match($uuid, $empresaId)) {
            return null;
        }

        // Enquanto a migração da coluna não rodou, a consulta segue sem ela.
        $senhaAlterada = self::temColunaSenhaAlteradaEm() ? 'u.senha_alterada_em' : 'NULL';

        $linha = db_connect()->query(
            // O criador da empresa (administrador principal) tem sempre acesso
            // total, mesmo que o cargo dele tenha perdido a marca: senão a
            // empresa pode ficar sem ninguém capaz de administrá-la.
            // O cargo é o da pessoa na equipe do token: em outra equipe ela pode ter outro.
            "SELECT m.cargo_id, (c.acesso_total OR e.administrador_principal_id = u.id) AS acesso_total,
                    {$senhaAlterada} AS senha_alterada_em,
                    EXISTS (SELECT 1 FROM token_revogado tr WHERE tr.jti = ?) AS revogado,
                    (SELECT COALESCE(json_agg(p.codigo), '[]')
                       FROM cargo_permissao cp JOIN permissao p ON p.id = cp.permissao_id
                      WHERE cp.cargo_id = m.cargo_id) AS permissoes,
                    (SELECT COALESCE(json_object_agg(cmp.modulo_id, cmp.nivel), '{}')
                       FROM cargo_modulo_permissao cmp
                      WHERE cmp.cargo_id = m.cargo_id) AS niveis
               FROM usuario u
               JOIN membro m ON m.usuario_id = u.id AND m.empresa_id = ?
               JOIN cargo c ON c.id = m.cargo_id
               JOIN empresa e ON e.id = m.empresa_id
              WHERE u.id = ?",
            [$jti, $empresaId, $usuarioId]
        )->getRowArray();

        if (! $linha) {
            return null;
        }

        return [
            'revogado'          => self::paraBooleano($linha['revogado']),
            'cargo_id'          => $linha['cargo_id'],
            'acesso_total'      => self::paraBooleano($linha['acesso_total'] ?? false),
            'permissoes'        => json_decode((string) $linha['permissoes'], true) ?: [],
            'niveis'            => json_decode((string) $linha['niveis'], true) ?: [],
            'senha_alterada_em' => $linha['senha_alterada_em'],
        ];
    }

    /**
     * A coluna `usuario.senha_alterada_em` vem de uma migração nova. Até ela
     * rodar, o código não pode depender dela (senão ninguém autentica). A
     * resposta fica em cache para não custar uma consulta por requisição.
     */
    public static function temColunaSenhaAlteradaEm(): bool
    {
        $emCache = cache('schema_usuario_senha_alterada_em');
        if ($emCache !== null) {
            return $emCache === 1;
        }

        $existe = db_connect()->fieldExists('senha_alterada_em', 'usuario');
        // Se ainda não existe, confere de novo em 10 minutos (a migração pode rodar a qualquer hora).
        cache()->save('schema_usuario_senha_alterada_em', $existe ? 1 : 0, $existe ? DAY : 600);

        return $existe;
    }

    /**
     * Normaliza um valor BOOLEAN vindo do banco para bool do PHP.
     *
     * Existe porque o driver Postgre pode devolver colunas BOOLEAN como
     * bool nativo, inteiro (0/1) ou string curta ('t'/'f') dependendo do
     * modo de fetch — filter_var(FILTER_VALIDATE_BOOLEAN) não cobre o
     * caso 't'/'f', então tratamos manualmente para não depender de um
     * formato específico de driver/config.
     */
    public static function paraBooleano(mixed $valor): bool
    {
        if (is_bool($valor)) {
            return $valor;
        }

        return in_array($valor, [1, '1', 't', 'true'], true);
    }
}
