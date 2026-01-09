# Sistema de Gestão de Vendas - Arquitetura Distribuída

Sistema cliente-servidor com arquitetura em camadas seguindo padrões de sistemas distribuídos.

**Nota**: O sistema segue a arquitetura de sistemas distribuídos com Stubs/Skeletons, como a Java RMI mas com implementação do
protocolo

Uso de DTOs
Os DTOs são usados para desacoplar cliente e servidor, permitindo que cada lado evolua independentemente. Enviam apenas dados necessários pela rede (não estruturas internas completas), reduzindo o payload e protegendo informação sensível. Facilitam validação centralizada, tornam a API clara e autodocumentada, e garantem que mudanças no servidor não quebrem o cliente.
