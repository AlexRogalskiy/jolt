package com.bazaarvoice.jolt.shiftr;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class PathElement {

    public static PathElement parse( String key )  {
        if ( key.contains("&") || key.contains("[") ) {
        if ( key.contains("*") )
        {
            throw new IllegalArgumentException("Can't mix * and ( & or [] ) ");
        }
        return new ReferencePathElement( key );
    }
    if ( key.contains("*") ) {
        return new StarPathElement( key );
    }
    if ( key.contains("@") ) {
        return new AtPathElement( key );
    }

    return new LiteralPathElement( key );
}

    public static List<PathElement> parse( String[] keys ) {
        ArrayList<PathElement> paths = new ArrayList<PathElement>();

        for( String key: keys ) {
            paths.add( parse( key ) );
        }
        return paths;
    }

    public static List<PathElement> parseDotNotation( String dotNotation ) {

        if ( dotNotation.contains("@") || dotNotation.contains("*") ) {
            throw new IllegalArgumentException("DotNotation parse (output key) can not contain '@' or '*'.");
        }

        if ( ( dotNotation == null ) || ( "".equals( dotNotation ) ) ) {   // TODO blank?
            return new ArrayList<PathElement>();
        } else {
            String[] split = dotNotation.split( "\\." );
            return PathElement.parse( split );
        }
    }

    protected String rawKey;

    public PathElement( String key ) {
        this.rawKey = key;
    }

    public String toString() {
        return rawKey;
    }

    public abstract String evaluateAsOutput( Path<String> realInputPath, Path<PathElement> specInputPath );

    public abstract PathElement matchInput( Path<String> realInputPath, Path<PathElement> specInputPath );

    // TODO only literal key should have this
    public abstract String getSubKeyRef( int index );

    public static class LiteralPathElement extends PathElement {

        private List<String> subKeys = new ArrayList<String>();

        public LiteralPathElement( String key ) {
            super(key);
            subKeys.add( key );
        }

        public LiteralPathElement( String key, List<String> subKeys ) {
            super(key);
            this.subKeys.add( key );
            this.subKeys.addAll( subKeys );
        }

        public String evaluateAsOutput( Path<String> realInputPath, Path<PathElement> specInputPath ) {
            return rawKey;
        }

        public PathElement matchInput( Path<String> realInputPath, Path<PathElement> specInputPath ) {
            return rawKey.equals( realInputPath.lastElement() ) ? this : null ;
        }

        public String getSubKeyRef( int index ) {
            return subKeys.get( index );
        }
    }

    public static class OrPathElement extends PathElement {

        List<PathElement> elements;
        public OrPathElement( String key ) {
            super(key);

            String[] split = key.split( "|" );
            elements = PathElement.parse( split );
        }

        public String evaluateAsOutput( Path<String> realInputPath, Path<PathElement> specInputPath ) {
            throw new UnsupportedOperationException("Don't call evaluateAsOutput on the '|'");
        }

        public PathElement matchInput( Path<String> realInputPath, Path<PathElement> specInputPath ) {
            for ( PathElement pe : elements ) {
                PathElement pathElement = pe.matchInput( realInputPath, specInputPath );
                if ( pathElement != null ) {
                    return pathElement;
                }
            }
            return null;
        }

        public String getSubKeyRef( int index ) {
            throw new UnsupportedOperationException("Don't call getSubKeyRef on the '|'");
        }

    }

    public static class AtPathElement extends PathElement {
        public AtPathElement( String key ) {
            super(key);
        }

        public String evaluateAsOutput( Path<String> realInputPath, Path<PathElement> specInputPath ) {
            throw new UnsupportedOperationException("Don't call evaluateAsOutput on the '@'");
        }

        public PathElement matchInput( Path<String> realInputPath, Path<PathElement> specInputPath ) {
            return this;
        }

        public String getSubKeyRef( int index ) {
            throw new UnsupportedOperationException("Don't call getSubKeyRef on the '@'");
        }
    }

    public static class StarPathElement extends PathElement {

        private Pattern pattern;
        private int groupCount = 0;

        public StarPathElement( String key ) {
            super(key);

            groupCount = StringUtils.countMatches( key, "*" );

            String regex = "^" + key.replace("*", "(.*?)")  + "$";

            // "rating-*-*"  ->  "^rating-(.*?)-(.*?)$"
            pattern = Pattern.compile( regex );
        }

        public String evaluateAsOutput( Path<String> realInputPath, Path<PathElement> specInputPath ) {
            throw new UnsupportedOperationException("Don't call evaluateAsOutput on the '*'");
        }

        public PathElement matchInput( Path<String> realInputPath, Path<PathElement> specInputPath ) {
            Matcher matcher = pattern.matcher( realInputPath.lastElement() );
            if ( ! matcher.find() ) {
                return null;
            }

            List<String> subKeys = new ArrayList<String>();
            int groupCount = matcher.groupCount();
            for ( int index = 0; index < groupCount; index++) {
                subKeys.add( matcher.group( index ) );
            }

            return new LiteralPathElement(realInputPath.lastElement(), subKeys);
        }

        @Override
        public String getSubKeyRef( int index ) {
            throw new UnsupportedOperationException("Don't call getSubKeyRef on the '*'");
        }
    }

    public static class ReferencePathElement extends PathElement {

        List tokens = new ArrayList();

        public ReferencePathElement( String key ) {
            super(key);

            StringBuffer literal = new StringBuffer();

            int index = 0;
            while( index < key.length() ) {

                char c = key.charAt( index );

                // beginning of reference
                if ( c == '&' || c == '[' ) {

                    // store off any literal text captured thus far
                    if ( literal.length() > 0 ) {
                        tokens.add( literal.toString() );
                        literal = new StringBuffer();
                    }
                    int subEnd = 0;
                    Reference ref = null;

                    if ( c == '[' ) {
                        subEnd = findEndOfArrayReference( key.substring( index ) );
                        ref = Reference.newReference(true, key.substring(index + 1, index + subEnd) ); // chomp off the leading and trailing [ ]
                    }
                    else {
                        subEnd = findEndOfReference( key.substring( index + 1 ) );
                        ref = Reference.newReference(false, key.substring(index, index + subEnd + 1) );
                    }
                    tokens.add( ref );
                    index += subEnd;
                }
                else {
                    literal.append( c );
                }
                index++;
            }
            if ( literal.length() > 0 ) {
                tokens.add( literal.toString() );
            }
        }

        private static int findEndOfArrayReference( String key ) {
            int endOfArray = key.indexOf( ']' );
            if ( endOfArray <= 0 ) {
                throw new IllegalArgumentException( "invalid array reference of " + key + "' " );
            }
            return endOfArray;
        }

        private static int findEndOfReference( String key ) {
            if( "".equals( key ) ) {
                return 0;
            }

            for( int index = 0; index < key.length(); index++ ){
                char c = key.charAt( index );
                if( ! Character.isDigit( c ) && c != '(' && c != ')' ) {
                    return index;
                }
            }
            return key.length();
        }

        public String evaluateAsOutput( Path<String> realInputPath, Path<PathElement> specInputPath ) {

            StringBuffer output = new StringBuffer();

            for ( Object token : tokens ) {
                if ( token instanceof String ) {
                    output.append( token );
                }
                else {
                    Reference ref = (Reference) token;

                    if ( ref.isArray ) {
                        if ( ref.arrayIndex != -1 ) {
                            output.append( "[" + ref.arrayIndex + "]");
                        }
                        else {
                            PathElement pe = specInputPath.elementFromEnd( ref.pathIndex );
                            String keyPart = pe.getSubKeyRef( ref.keyGroup );
                            int index = Integer.getInteger( keyPart );
                            output.append( "[" + index + "]");
                        }
                    }
                    else {
                        PathElement pe = specInputPath.elementFromEnd( ref.pathIndex );
                        String keyPart = pe.getSubKeyRef( ref.keyGroup );
                        output.append( keyPart );
                    }
                }
            }

            return output.toString();
        }

        @Override
        public PathElement matchInput( Path<String> realInputPath, Path<PathElement> specInputPath ) {
            String evaled = evaluateAsOutput( realInputPath, specInputPath );
            if ( evaled.equals( realInputPath.lastElement() ) ) {
                return new LiteralPathElement( evaled );
            }
            return null;
        }

        @Override
        public String getSubKeyRef( int index ) {
            throw new UnsupportedOperationException("Don't call getSubKeyRef on the '&'");
        }

    }
}