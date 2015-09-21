# Założenia

* aplikacja powinna być workiem na boty, obsługiwać wiele botów
* jednostką konfiguracji jest bot
* z botem powinien rozmawiać implementowany przez użytkownika aktor, wykonujący logikę
* a więc BotActor powinien zajmować się
    * odbieraniem rozkazów od UserActora
    * przesyłać je do api telegrama
    * rejestrować się na aktualizacje
        * z życiem poolingu
        * z użyciem webhooka
    * przekazywać aktualizacje do UserActora
    * zarządzać persystencją aktualizacji?
* webhooki niby mogą być realizowane przez różne instancje serwisu WebHook, ale w sumie jeden serwis
bez problemu, tylko routing trzeba zamknąć we frameworku, może rozwiązaniem byłoby uruchomienie pary:
aktor, ktory sam zarządzałby sobie serwisem, przyjmowałby rejestracje webhooków od BotActorów, rejestrował
webhoki w api telegrama, oraz wszystko co przyjdzie odsyłał BotActorom
* aktorzy od long poolingu moglibybyć samodzielni (1/BotActor) i zajmowaliby się pobieraniem aktualizacji
* w obu przypadkach aktualizacje powinny być ściągane z serwera raz, i zapisywane gdzieś,
 w przypadku webhooków muszą być zapisane, bo aktualizacje webhookiem przychodzą tylko raz, takie zapisane
 aktualizacje powinny mieć TTL (kilka dni) żeby nie zasyfiły serwera
    * aktualizacja, UUID, TTL
        * w RAMie
        * w mongo
* persystencja wiadomości powinna być nieblokująca, najprościej opcjonalna w BotActor

# Podsumowanie

## jednostki we frameworku

* WebHooksManagement - aktor + serwis, zarządza webhookami, pobiera aktualizacje, rejestruje BotActorów na webhooki
* CacheActor - aktor zarządzający persystencją aktualizacji, dwie implementacje - RAM, MONGO
* BotActor - aktor, zarządza komunikacją z Api Telegrama, odbiera aktualizacje, komunikuje się z Aktorem definiowanym
* UpdatesActor - pośredniczący w pobieraniu aktualizacji - dwie implementacje, Webhooks i LongPool
przez użytkownika, ewentualnie komunikujący się z cache aktorem
* BotMother - aktor inicjalizujący nowy ekosystemik dla bota
* UserActor - aktor realizujący logikę biznesową Bota, implementowany przez użytkownika


## inicjalizacja

BotMother bierze referencję do UserBota i inicjalizuje pozostałych aktorów, wysyła useractorowi wiadomość
ze wszytkimi referencjami

Cache aktor może być podany przez referencję
Updates actor też może być podany przez referencję

WebHookManagement, powininen leniwie podłączać sie do portów itd, tak będzie wygodniej, chyba

