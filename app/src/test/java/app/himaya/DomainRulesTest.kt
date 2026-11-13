package app.himaya
import org.junit.Assert.*
import org.junit.Test
class DomainRulesTest {
    @Test fun normalizesDomainsUrlsAndIdn(){
        assertEquals("example.com",DomainRules.normalize(" EXAMPLE.com. "))
        assertEquals("example.com",DomainRules.normalize("HTTPS://example.com/a?b=c"))
        assertEquals("xn--e1afmkfd.xn--p1ai",DomainRules.normalize("пример.рф"))
        assertEquals("xn--e1afmkfd.xn--p1ai",DomainRules.normalize("https://пример.рф/page"))
    }
    @Test fun rejectsAmbiguousOrInvalidInput(){
        for(s in listOf("","localhost","127.0.0.1","*.example.com","example.com/path","https://user@example.com","https://example.com:443","ftp://example.com","a..com","-a.com","a-.com","hello world.com","a".repeat(64)+".com","https://[::1]"))assertNull(s,DomainRules.normalize(s))
    }
    @Test fun matchesOnlyExactDomainsAndSubdomains(){
        val rules=setOf("example.com")
        assertTrue(DomainRules.matches("example.com",rules));assertTrue(DomainRules.matches("www.example.com",rules))
        assertFalse(DomainRules.matches("notexample.com",rules));assertFalse(DomainRules.matches("example.com.other.org",rules))
    }
}
