/*
 * HA-JDBC: High-Availability JDBC
 * Copyright (C) 2004 Paul Ferraro
 * 
 * This library is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by the 
 * Free Software Foundation; either version 2.1 of the License, or (at your 
 * option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation, 
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * Contact: ferraro@users.sourceforge.net
 */
package net.sf.hajdbc;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contains a map of <code>Database</code> -&gt; database connection factory (i.e. Driver, DataSource, ConnectionPoolDataSource, XADataSource)
 * @author  Paul Ferraro
 * @version $Revision$
 * @since   1.0
 */
public class DatabaseCluster extends SQLProxy implements DatabaseClusterMBean
{
	private Set activeDatabaseSet = new LinkedHashSet();
	private DatabaseClusterDescriptor descriptor;
	
	/**
	 * Constructs a new DatabaseCluster.
	 * @param descriptor
	 * @param databaseMap
	 */
	protected DatabaseCluster(DatabaseClusterDescriptor descriptor, Map databaseConnectorMap)
	{
		super(databaseConnectorMap);
		
		this.descriptor = descriptor;
		
		Iterator databases = this.descriptor.getDatabaseMap().values().iterator();
		
		while (databases.hasNext())
		{
			Database database = (Database) databases.next();
			
			if (this.isActive(database))
			{
				this.activeDatabaseSet.add(database);
			}
		}
	}
	
	/**
	 * @param database
	 * @return true if the specified database is active, false otherwise
	 */
	public boolean isActive(Database database)
	{
		Connection connection = null;
		Statement statement = null;
		
		Object databaseConnector = this.getSQLObject(database);
		
		try
		{
			connection = database.connect(databaseConnector);
			
			statement = connection.createStatement();
			
			statement.execute(this.descriptor.getValidateSQL());
			
			return true;
		}
		catch (java.sql.SQLException e)
		{
			return false;
		}
		finally
		{
			if (statement != null)
			{
				try
				{
					statement.close();
				}
				catch (java.sql.SQLException e)
				{
					// Ignore
				}
			}

			if (connection != null)
			{
				try
				{
					connection.close();
				}
				catch (java.sql.SQLException e)
				{
					// Ignore
				}
			}
		}
	}
	
	/**
	 * Deactivates the specified database.
	 * @param database
	 * @return true if the database was successfully deactivated, false if it was already deactivated
	 */
	public boolean deactivate(Database database)
	{
		synchronized (this.activeDatabaseSet)
		{
			return this.activeDatabaseSet.remove(database);
		}
	}

	/**
	 * @see net.sf.hajdbc.DatabaseClusterMBean#getName()
	 */
	public String getName()
	{
		return this.descriptor.getName();
	}

	private Database getDatabase(String databaseId)
	{
		return (Database) this.descriptor.getDatabaseMap().get(databaseId);
	}
	
	/**
	 * @see net.sf.hajdbc.DatabaseClusterMBean#isActive(java.lang.String)
	 */
	public boolean isActive(String databaseId)
	{
		return this.isActive(this.getDatabase(databaseId));
	}

	/**
	 * @see net.sf.hajdbc.DatabaseClusterMBean#deactivate(java.lang.String)
	 */
	public void deactivate(String databaseId)
	{
		this.deactivate(this.getDatabase(databaseId));
	}

	/**
	 * @see net.sf.hajdbc.DatabaseClusterMBean#activate(java.lang.String, java.lang.String)
	 */
	public void activate(String databaseId, String strategyClassName) throws java.sql.SQLException
	{
		Database database = this.getDatabase(databaseId);
		
		try
		{
			Class strategyClass = Class.forName(strategyClassName);
			
			if (!DatabaseActivationStrategy.class.isAssignableFrom(strategyClass))
			{
				throw new SQLException("");
			}
			
			DatabaseActivationStrategy strategy = (DatabaseActivationStrategy) strategyClass.newInstance();
			
			strategy.activate(this, database);
		}
		catch (ClassNotFoundException e)
		{
			throw new SQLException(e);
		}
		catch (InstantiationException e)
		{
			throw new SQLException(e);
		}
		catch (IllegalAccessException e)
		{
			throw new SQLException(e);
		}
	}
	
	/**
	 * Returns the first database in the cluster
	 * @return the first database in the cluster
	 * @throws SQLException
	 */
	public Database firstDatabase() throws SQLException
	{
		synchronized (this.activeDatabaseSet)
		{
			if (this.activeDatabaseSet.size() == 0)
			{
				throw new SQLException("No active databases in cluster");
			}
			
			return (Database) this.activeDatabaseSet.iterator().next();
		}
	}
	
	/**
	 * Returns the next database in the cluster
	 * @return the next database in the cluster
	 * @throws SQLException
	 */
	public Database nextDatabase() throws SQLException
	{
		synchronized (this.activeDatabaseSet)
		{
			Database database = this.firstDatabase();
			
			if (this.activeDatabaseSet.size() > 1)
			{
				this.activeDatabaseSet.remove(database);
				
				this.activeDatabaseSet.add(database);
			}
			
			return database;
		}
	}

	/**
	 * A list of active databases in this cluster
	 * @return a list of Database objects
	 * @throws SQLException
	 */
	public List getActiveDatabaseList() throws SQLException
	{
		synchronized (this.activeDatabaseSet)
		{
			if (this.activeDatabaseSet.size() == 0)
			{
				throw new SQLException("No active databases in cluster");
			}
			
			return new ArrayList(this.activeDatabaseSet);
		}
	}

	/**
	 * @see net.sf.hajdbc.SQLProxy#getDatabaseCluster()
	 */
	protected DatabaseCluster getDatabaseCluster()
	{
		return this;
	}
}
