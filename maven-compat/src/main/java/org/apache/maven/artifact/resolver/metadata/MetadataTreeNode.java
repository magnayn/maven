package org.apache.maven.artifact.resolver.metadata;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactScopeEnum;
/**
 * metadata [dirty] Tree
 * 
 * @author <a href="oleg@codehaus.org">Oleg Gusakov</a>
 *
 */

public class MetadataTreeNode
{
    ArtifactMetadata md; // this node

    MetadataTreeNode parent; // papa

    /** default # of children. Used for tree creation optimization only */
    int nChildren = 8;

    MetadataTreeNode[] children; // of cause

    public int getNChildren()
	{
		return nChildren;
	}

	public void setNChildren(int children)
	{
		nChildren = children;
	}

	//------------------------------------------------------------------------
    public MetadataTreeNode()
    {
    }
    //------------------------------------------------------------------------
    public MetadataTreeNode( ArtifactMetadata md,
                             MetadataTreeNode parent,
                             boolean resolved,
                             ArtifactScopeEnum scope )
    {
        if ( md != null )
        {
            md.setArtifactScope( ArtifactScopeEnum.checkScope(scope) );
            md.setResolved(resolved);
        }

        this.md = md;
        this.parent = parent;
    }
    //------------------------------------------------------------------------
    public MetadataTreeNode( Artifact af,
                             MetadataTreeNode parent,
                             boolean resolved,
                             ArtifactScopeEnum scope
                           )
    {
        this( new ArtifactMetadata( af ), parent, resolved, scope );
    }
    //------------------------------------------------------------------------
    public void addChild( int index, MetadataTreeNode kid )
    {
        if ( kid == null )
        {
            return;
        }

        if( children == null )
        	children = new MetadataTreeNode[nChildren];
        
        children[index % nChildren] = kid;
    }
    //------------------------------------------------------------------
    @Override
    public String toString()
    {
        return md == null ? "no metadata" : md.toString();
    }
    //------------------------------------------------------------------
    public String graphHash()
        throws MetadataResolutionException
    {
        if ( md == null )
        {
            throw new MetadataResolutionException( "treenode without metadata, parent: "
                + ( parent == null ? "null" : parent.toString() )
            );
        }

        return md.groupId + ":" + md.artifactId;
    }

    //------------------------------------------------------------------------
    public boolean hasChildren()
    {
        return children != null;
    }
    //------------------------------------------------------------------------
    public ArtifactMetadata getMd()
    {
        return md;
    }

    public void setMd( ArtifactMetadata md )
    {
        this.md = md;
    }

    public MetadataTreeNode getParent()
    {
        return parent;
    }

    public void setParent( MetadataTreeNode parent )
    {
        this.parent = parent;
    }

    public MetadataTreeNode[] getChildren()
    {
        return children;
    }

    public void setChildren( MetadataTreeNode[] children )
    {
        this.children = children;
    }
    //------------------------------------------------------------------------
    //------------------------------------------------------------------------

}
