/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.metamodel.mapping.inheritance.joined;

import java.sql.Statement;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.JoinedSubclassEntityPersister;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Andrea Boriero
 */

@DomainModel(
		annotatedClasses = {
				JoinedInheritanceWithConcreteRootTest.Customer.class,
				JoinedInheritanceWithConcreteRootTest.DomesticCustomer.class,
				JoinedInheritanceWithConcreteRootTest.ForeignCustomer.class
		}
)
@ServiceRegistry
@SessionFactory
public class JoinedInheritanceWithConcreteRootTest {
	@Test
	public void basicTest(SessionFactoryScope scope) {
		final EntityPersister customerDescriptor = scope.getSessionFactory()
				.getMetamodel()
				.findEntityDescriptor( Customer.class );
		final EntityPersister domesticCustomerDescriptor = scope.getSessionFactory()
				.getMetamodel()
				.findEntityDescriptor( DomesticCustomer.class );
		final EntityPersister foreignCustomerDescriptor = scope.getSessionFactory()
				.getMetamodel()
				.findEntityDescriptor( ForeignCustomer.class );

		assert customerDescriptor instanceof JoinedSubclassEntityPersister;

		assert customerDescriptor.isTypeOrSuperType( customerDescriptor );
		assert !customerDescriptor.isTypeOrSuperType( domesticCustomerDescriptor );
		assert !customerDescriptor.isTypeOrSuperType( foreignCustomerDescriptor );

		assert domesticCustomerDescriptor instanceof JoinedSubclassEntityPersister;

		assert domesticCustomerDescriptor.isTypeOrSuperType( customerDescriptor );
		assert domesticCustomerDescriptor.isTypeOrSuperType( domesticCustomerDescriptor );
		assert !domesticCustomerDescriptor.isTypeOrSuperType( foreignCustomerDescriptor );

		assert foreignCustomerDescriptor instanceof JoinedSubclassEntityPersister;

		assert foreignCustomerDescriptor.isTypeOrSuperType( customerDescriptor );
		assert !foreignCustomerDescriptor.isTypeOrSuperType( domesticCustomerDescriptor );
		assert foreignCustomerDescriptor.isTypeOrSuperType( foreignCustomerDescriptor );
	}

	@Test
	public void rootQueryExecutionTest(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					{
						// [name, taxId, vat]
						final List<Customer> results = session.createQuery(
								"select c from Customer c",
								Customer.class
						).list();

						assertThat( results.size(), is( 3 ) );
						boolean foundDomesticCustomer = false;
						boolean foundForeignCustomer = false;
						boolean foundCustomer = false;
						for ( Customer result : results ) {
							if ( result.getId() == 1 ) {
								assertThat( result, instanceOf( DomesticCustomer.class ) );
								final DomesticCustomer customer = (DomesticCustomer) result;
								assertThat( customer.getName(), is( "domestic" ) );
								assertThat( ( customer ).getTaxId(), is( "123" ) );
								foundDomesticCustomer = true;
							}
							else if ( result.getId() == 2 ) {
								final ForeignCustomer customer = (ForeignCustomer) result;
								assertThat( customer.getName(), is( "foreign" ) );
								assertThat( ( customer ).getVat(), is( "987" ) );
								foundForeignCustomer = true;
							}
							else {
								assertThat( result.getId(), is( 3 ) );
								final Customer customer = result;
								assertThat( customer.getName(), is( "customer" ) );
								foundCustomer = true;
							}
						}
						assertTrue( foundDomesticCustomer );
						assertTrue( foundForeignCustomer );
						assertTrue( foundCustomer );
					}
				}
		);
	}

	@Test
	public void subclassQueryExecutionTest(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					{
						final DomesticCustomer result = session.createQuery(
								"select c from DomesticCustomer c",
								DomesticCustomer.class
						).uniqueResult();

						assertThat( result, notNullValue() );
						assertThat( result.getId(), is( 1 ) );
						assertThat( result.getName(), is( "domestic" ) );
						assertThat( result.getTaxId(), is( "123" ) );
					}

					{
						final ForeignCustomer result = session.createQuery(
								"select c from ForeignCustomer c",
								ForeignCustomer.class
						).uniqueResult();

						assertThat( result, notNullValue() );
						assertThat( result.getId(), is( 2 ) );
						assertThat( result.getName(), is( "foreign" ) );
						assertThat( result.getVat(), is( "987" ) );
					}
				}
		);
	}

	@BeforeEach
	public void createTestData(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.persist( new DomesticCustomer( 1, "domestic", "123" ) );
					session.persist( new ForeignCustomer( 2, "foreign", "987" ) );
					session.persist( new Customer( 3, "customer" ) );
				}
		);
	}

	@AfterEach
	public void cleanupTestData(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.doWork(
							work -> {
								Statement statement = work.createStatement();
								try {
									statement.execute( "delete from DomesticCustomer" );
									statement.execute( "delete from ForeignCustomer" );
									statement.execute( "delete from Customer" );
								}
								finally {
									statement.close();
								}
							}
					);
				}
		);
	}

	@Entity(name = "Customer")
	@Inheritance(strategy = InheritanceType.JOINED)
	@Table(name = "Customer")
	public static class Customer {
		private Integer id;
		private String name;

		public Customer() {
		}

		public Customer(Integer id, String name) {
			this.id = id;
			this.name = name;
		}

		@Id
		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@Entity(name = "DomesticCustomer")
	@Table(name = "DomesticCustomer")
	public static class DomesticCustomer extends Customer {
		private String taxId;

		public DomesticCustomer() {
		}

		public DomesticCustomer(Integer id, String name, String taxId) {
			super( id, name );
			this.taxId = taxId;
		}

		public String getTaxId() {
			return taxId;
		}

		public void setTaxId(String taxId) {
			this.taxId = taxId;
		}
	}

	@Entity(name = "ForeignCustomer")
	@Table(name = "ForeignCustomer")
	public static class ForeignCustomer extends Customer {
		private String vat;

		public ForeignCustomer() {
		}

		public ForeignCustomer(Integer id, String name, String vat) {
			super( id, name );
			this.vat = vat;
		}

		public String getVat() {
			return vat;
		}

		public void setVat(String vat) {
			this.vat = vat;
		}
	}
}

